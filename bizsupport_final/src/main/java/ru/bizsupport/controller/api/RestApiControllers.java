package ru.bizsupport.controller.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.bizsupport.dto.request.*;
import ru.bizsupport.dto.response.ApiResponses.*;
import ru.bizsupport.dto.response.DtoMapper;
import ru.bizsupport.entity.*;
import ru.bizsupport.repository.UserRepository;
import ru.bizsupport.service.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

// ══════════════════════════════════════════════════════════════
//  AUTH API — /api/auth
// ══════════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
class AuthApiController {

    private final AuthService authService;

    @PostMapping("/register")
    ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        try {
            User user = authService.register(req);
            String token = authService.login(
                    new LoginRequest() {{ setEmail(req.getEmail()); setPassword(req.getPassword()); }});
            return ResponseEntity.ok(AuthResponse.builder()
                    .token(token)
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(400, "Bad Request", e.getMessage()));
        }
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            String token = authService.login(req);
            User user = authService.getCurrentUser(req.getEmail());
            return ResponseEntity.ok(AuthResponse.builder()
                    .token(token)
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(new ErrorResponse(401, "Unauthorized", "Неверный email или пароль"));
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  DASHBOARD API — /api/dashboard
// ══════════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
class DashboardApiController {

    private final UserRepository userRepo;
    private final CompanyProfileService profileService;
    private final TaxService taxService;
    private final ProcurementService procurementService;

    @GetMapping
    ResponseEntity<DashboardResponse> dashboard(@AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        DashboardResponse.DashboardResponseBuilder builder = DashboardResponse.builder()
                .user(DtoMapper.toDto(user))
                .checklists(Collections.emptyList())
                .currentRegimes(Collections.emptyList());

        profileService.findByUserId(user.getId()).ifPresent(profile -> {
            List<CompanyTaxRegime> regimes = taxService.getCompanyCurrentRegimes(profile.getId());
            builder.profile(DtoMapper.toDto(profile, regimes));
            builder.currentRegimes(regimes.stream().map(DtoMapper::toDto).collect(Collectors.toList()));
            builder.checklists(procurementService.getUserChecklists(user.getId())
                    .stream().map(DtoMapper::toDto).collect(Collectors.toList()));
        });

        return ResponseEntity.ok(builder.build());
    }
}

// ══════════════════════════════════════════════════════════════
//  PROFILE API — /api/profile
// ══════════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
class ProfileApiController {

    private final UserRepository userRepo;
    private final CompanyProfileService profileService;
    private final TaxService taxService;

    @GetMapping
    ResponseEntity<?> getProfile(@AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        return profileService.findByUserId(user.getId())
                .map(profile -> {
                    List<CompanyTaxRegime> regimes = taxService.getCompanyCurrentRegimes(profile.getId());
                    return ResponseEntity.ok(DtoMapper.toDto(profile, regimes));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    ResponseEntity<CompanyProfileResponse> saveProfile(
            @AuthenticationPrincipal UserDetails ud,
            @Valid @RequestBody CompanyProfileRequest req) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        CompanyProfile profile = profileService.saveOrUpdate(user.getId(), req);
        List<CompanyTaxRegime> regimes = taxService.getCompanyCurrentRegimes(profile.getId());
        return ResponseEntity.ok(DtoMapper.toDto(profile, regimes));
    }

    @GetMapping("/me")
    ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        return ResponseEntity.ok(DtoMapper.toDto(user));
    }
}

// ══════════════════════════════════════════════════════════════
//  TAX API — /api/tax
// ══════════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/tax")
@RequiredArgsConstructor
class TaxApiController {

    private final UserRepository userRepo;
    private final CompanyProfileService profileService;
    private final TaxService taxService;
    private final TaxCalculatorService calculatorService;

    /** Все налоговые режимы */    @GetMapping("/regimes")
    ResponseEntity<List<TaxRegimeResponse>> allRegimes() {
        return ResponseEntity.ok(
                taxService.getAllRegimes().stream()
                        .map(DtoMapper::toDto)
                        .collect(Collectors.toList()));
    }

    /** Детали режима: обязательства + дедлайны */
    @GetMapping("/regimes/{code}")
    ResponseEntity<?> regimeDetail(@PathVariable String code) {
        return taxService.findByCode(code)
                .map(regime -> {
                    TaxRegimeDetailResponse detail = TaxRegimeDetailResponse.builder()
                            .regime(DtoMapper.toDto(regime))
                            .obligations(taxService.getObligationsForRegime(regime.getId())
                                    .stream().map(DtoMapper::toDto).collect(Collectors.toList()))
                            .deadlines(taxService.getTemplateDeadlines(regime.getId())
                                    .stream().map(DtoMapper::toDto).collect(Collectors.toList()))
                            .build();
                    return ResponseEntity.ok(detail);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Рекомендация подходящих режимов */
    @GetMapping("/recommend")
    ResponseEntity<List<TaxRegimeResponse>> recommend(
            @RequestParam String type,
            @RequestParam(required = false) Integer employees,
            @RequestParam(required = false) BigDecimal revenue) {
        CompanyProfile.CompanyType ct = CompanyProfile.CompanyType.valueOf(type);
        return ResponseEntity.ok(
                taxService.recommend(ct, employees, revenue).stream()
                        .map(DtoMapper::toDto)
                        .collect(Collectors.toList()));
    }

    /** Текущие режимы компании */
    @GetMapping("/current")
    ResponseEntity<?> currentRegimes(@AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        return profileService.findByUserId(user.getId())
                .map(profile -> ResponseEntity.ok(
                        taxService.getCompanyCurrentRegimes(profile.getId()).stream()
                                .map(DtoMapper::toDto)
                                .collect(Collectors.toList())))
                .orElse(ResponseEntity.ok(Collections.emptyList()));
    }

    /** Установить режим налогообложения */
    @PostMapping("/set-regime")
    ResponseEntity<MessageResponse> setRegime(
            @AuthenticationPrincipal UserDetails ud,
            @RequestParam String code) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        profileService.findByUserId(user.getId()).ifPresent(p ->
                profileService.setTaxRegime(p.getId(), code));
        return ResponseEntity.ok(new MessageResponse("Налоговый режим установлен: " + code));
    }

    /** Дедлайны компании */
    @GetMapping("/deadlines")
    ResponseEntity<?> deadlines(@AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        return profileService.findByUserId(user.getId())
                .map(profile -> ResponseEntity.ok(
                        taxService.getCompanyDeadlines(profile.getId()).stream()
                                .map(DtoMapper::toDto)
                                .collect(Collectors.toList())))
                .orElse(ResponseEntity.ok(Collections.emptyList()));
    }

    /** Калькулятор налоговой нагрузки */
    @GetMapping("/calculator")
    ResponseEntity<List<TaxCalculatorService.TaxCalcResult>> calculate(
            @RequestParam BigDecimal revenue,
            @RequestParam(required = false, defaultValue = "0") BigDecimal expenses,
            @RequestParam(required = false, defaultValue = "0") int employees,
            @RequestParam(required = false, defaultValue = "0") BigDecimal avgSalary,
            @RequestParam(required = false, defaultValue = "true") boolean ip) {
        var input = TaxCalculatorService.TaxCalcInput.builder()
                .revenue(revenue)
                .expenses(expenses)
                .employees(employees)
                .avgSalary(avgSalary)
                .ip(ip)
                .build();
        return ResponseEntity.ok(calculatorService.calculate(input));
    }
}

// ══════════════════════════════════════════════════════════════
//  PROCUREMENT API — /api/procurement
// ══════════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/procurement")
@RequiredArgsConstructor
class ProcurementApiController {

    private final UserRepository userRepo;
    private final CompanyProfileService profileService;
    private final ProcurementService procurementService;

    /** Все сценарии (или только МСП для МСП-компаний) */
    @GetMapping("/scenarios")
    ResponseEntity<List<ScenarioResponse>> scenarios(@AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        boolean isMsp = profileService.findByUserId(user.getId())
                .map(CompanyProfile::getIsMsp).orElse(false);
        List<ProcurementScenario> list = isMsp
                ? procurementService.getMspScenarios()
                : procurementService.getAllScenarios();
        return ResponseEntity.ok(
                list.stream().map(DtoMapper::toDto).collect(Collectors.toList()));
    }

    /** Детали сценария: риски + шаблоны чек-листов */
    @GetMapping("/scenarios/{id}")
    ResponseEntity<ScenarioDetailResponse> scenarioDetail(@PathVariable Long id) {
        ProcurementScenario scenario = procurementService.getScenarioById(id);
        return ResponseEntity.ok(ScenarioDetailResponse.builder()
                .scenario(DtoMapper.toDto(scenario))
                .risks(procurementService.getRisksForScenario(id)
                        .stream().map(DtoMapper::toDto).collect(Collectors.toList()))
                .templates(procurementService.getTemplates()
                        .stream().map(DtoMapper::toDto).collect(Collectors.toList()))
                .build());
    }

    /** Мои чек-листы */
    @GetMapping("/checklists")
    ResponseEntity<List<ChecklistResponse>> myChecklists(@AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        return ResponseEntity.ok(
                procurementService.getUserChecklists(user.getId()).stream()
                        .map(DtoMapper::toDto)
                        .collect(Collectors.toList()));
    }

    /** Детали чек-листа */
    @GetMapping("/checklists/{id}")
    ResponseEntity<ChecklistResponse> checklistDetail(@PathVariable Long id) {
        return ResponseEntity.ok(DtoMapper.toDto(procurementService.getChecklist(id)));
    }

    /** Скопировать шаблон чек-листа */
    @PostMapping("/checklists/copy/{templateId}")
    ResponseEntity<ChecklistResponse> copyTemplate(
            @PathVariable Long templateId,
            @AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        Checklist cl = procurementService.copyTemplateForUser(templateId, user.getId());
        return ResponseEntity.ok(DtoMapper.toDto(cl));
    }

    /** Переключить шаг чек-листа */
    @PostMapping("/checklists/step/{stepId}/toggle")
    ResponseEntity<ChecklistStepResponse> toggleStep(@PathVariable Long stepId) {
        ChecklistStep step = procurementService.toggleStep(stepId);
        return ResponseEntity.ok(DtoMapper.toDto(step));
    }

    /** Шаблоны чек-листов */
    @GetMapping("/templates")
    ResponseEntity<List<ChecklistResponse>> templates() {
        return ResponseEntity.ok(
                procurementService.getTemplates().stream()
                        .map(DtoMapper::toDto)
                        .collect(Collectors.toList()));
    }
}

// ══════════════════════════════════════════════════════════════
//  SEARCH API — /api/search
// ══════════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
class SearchApiController {

    private final TaxService taxService;
    private final ProcurementService procurementService;

    @GetMapping
    ResponseEntity<List<SearchResultResponse>> search(@RequestParam(required = false) String q) {
        if (q == null || q.isBlank()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        String lq = q.toLowerCase();
        List<SearchResultResponse> results = new ArrayList<>();

        taxService.getAllRegimes().stream()
                .filter(r -> r.getName().toLowerCase().contains(lq)
                        || (r.getDescription() != null && r.getDescription().toLowerCase().contains(lq))
                        || r.getCode().toLowerCase().contains(lq))
                .forEach(r -> results.add(SearchResultResponse.builder()
                        .title(r.getName())
                        .snippet(trunc(r.getDescription(), 120))
                        .url("/api/tax/regimes/" + r.getCode())
                        .type("TAX")
                        .build()));

        procurementService.getAllScenarios().stream()
                .filter(s -> s.getTitle().toLowerCase().contains(lq)
                        || (s.getDescription() != null && s.getDescription().toLowerCase().contains(lq))
                        || s.getLawType().getDisplayName().toLowerCase().contains(lq))
                .forEach(s -> results.add(SearchResultResponse.builder()
                        .title(s.getTitle())
                        .snippet(trunc(s.getDescription(), 120))
                        .url("/api/procurement/scenarios/" + s.getId())
                        .type("PROCUREMENT")
                        .build()));

        return ResponseEntity.ok(results);
    }

    private String trunc(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}

// ══════════════════════════════════════════════════════════════
//  NOTIFICATIONS API — /api/notifications
// ══════════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
class NotificationApiController {

    private final UserRepository userRepo;
    private final ru.bizsupport.service.NotificationService notificationService;

    /** Все уведомления пользователя */
    @GetMapping
    ResponseEntity<List<NotificationResponse>> all(@AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        return ResponseEntity.ok(
                notificationService.getUserNotifications(user.getId()).stream()
                        .map(NotificationApiController::toDto)
                        .collect(Collectors.toList()));
    }

    /** Только непрочитанные */
    @GetMapping("/unread")
    ResponseEntity<List<NotificationResponse>> unread(@AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        return ResponseEntity.ok(
                notificationService.getUnreadNotifications(user.getId()).stream()
                        .map(NotificationApiController::toDto)
                        .collect(Collectors.toList()));
    }

    /** Количество непрочитанных */
    @GetMapping("/count")
    ResponseEntity<Map<String, Long>> count(@AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        return ResponseEntity.ok(Map.of("count", notificationService.countUnread(user.getId())));
    }

    /** Отметить одно как прочитанное */
    @PostMapping("/{id}/read")
    ResponseEntity<MessageResponse> markRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok(new MessageResponse("Уведомление отмечено как прочитанное"));
    }

    /** Отметить все как прочитанные */
    @PostMapping("/read-all")
    ResponseEntity<MessageResponse> markAllRead(@AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok(new MessageResponse("Все уведомления отмечены как прочитанные"));
    }

    /** Удалить уведомление */
    @DeleteMapping("/{id}")
    ResponseEntity<MessageResponse> delete(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return ResponseEntity.ok(new MessageResponse("Уведомление удалено"));
    }

    // ── DTO ───────────────────────────────────────────────────
    @lombok.Data @lombok.Builder
    static class NotificationResponse {
        private Long id;
        private String title;
        private String message;
        private Boolean isRead;
        private java.time.LocalDateTime sendAt;
        private java.time.LocalDateTime createdAt;
        private Long deadlineId;
        private String deadlineTitle;
    }

    private static NotificationResponse toDto(ru.bizsupport.entity.Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .isRead(n.getIsRead())
                .sendAt(n.getSendAt())
                .createdAt(n.getCreatedAt())
                .deadlineId(n.getDeadline() != null ? n.getDeadline().getId() : null)
                .deadlineTitle(n.getDeadline() != null ? n.getDeadline().getTitle() : null)
                .build();
    }
}

// ══════════════════════════════════════════════════════════════
//  FAVORITES API — /api/favorites
// ══════════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
class FavoriteApiController {

    private final UserRepository userRepo;
    private final ru.bizsupport.service.FavoriteService favoriteService;

    /** Все избранные пользователя */
    @GetMapping
    ResponseEntity<List<FavoriteResponse>> all(@AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        return ResponseEntity.ok(
                favoriteService.getUserFavorites(user.getId()).stream()
                        .map(FavoriteApiController::toDto)
                        .collect(Collectors.toList()));
    }

    /** Избранные по типу */
    @GetMapping("/type/{type}")
    ResponseEntity<List<FavoriteResponse>> byType(
            @PathVariable String type,
            @AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        EntityType entityType = EntityType.valueOf(type);
        return ResponseEntity.ok(
                favoriteService.getUserFavoritesByType(user.getId(), entityType).stream()
                        .map(FavoriteApiController::toDto)
                        .collect(Collectors.toList()));
    }

    /** Проверить, в избранном ли */
    @GetMapping("/check")
    ResponseEntity<Map<String, Boolean>> check(
            @RequestParam String type,
            @RequestParam Long entityId,
            @AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        boolean fav = favoriteService.isFavorite(user.getId(), EntityType.valueOf(type), entityId);
        return ResponseEntity.ok(Map.of("isFavorite", fav));
    }

    /** Переключить избранное (toggle) */
    @PostMapping("/toggle")
    ResponseEntity<Map<String, Object>> toggle(
            @RequestParam String type,
            @RequestParam Long entityId,
            @AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        boolean added = favoriteService.toggle(user.getId(), EntityType.valueOf(type), entityId);
        return ResponseEntity.ok(Map.of(
                "action", added ? "added" : "removed",
                "isFavorite", added));
    }

    @lombok.Data @lombok.Builder
    static class FavoriteResponse {
        private Long id;
        private String entityType;
        private String entityTypeDisplay;
        private Long entityId;
        private java.time.LocalDateTime createdAt;
    }

    private static FavoriteResponse toDto(ru.bizsupport.entity.UserFavorite f) {
        return FavoriteResponse.builder()
                .id(f.getId())
                .entityType(f.getEntityType().name())
                .entityTypeDisplay(f.getEntityType().getDisplayName())
                .entityId(f.getEntityId())
                .createdAt(f.getCreatedAt())
                .build();
    }
}

// ══════════════════════════════════════════════════════════════
//  LEGAL REFERENCES API — /api/legal
// ══════════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/legal")
@RequiredArgsConstructor
class LegalReferenceApiController {

    private final ru.bizsupport.service.LegalReferenceService legalRefService;

    /** Правовые ссылки для конкретной сущности */
    @GetMapping
    ResponseEntity<List<LegalRefResponse>> getReferences(
            @RequestParam String type,
            @RequestParam Long entityId) {
        return ResponseEntity.ok(
                legalRefService.getReferences(EntityType.valueOf(type), entityId).stream()
                        .map(LegalReferenceApiController::toDto)
                        .collect(Collectors.toList()));
    }

    /** Все ссылки по типу */
    @GetMapping("/type/{type}")
    ResponseEntity<List<LegalRefResponse>> allByType(@PathVariable String type) {
        return ResponseEntity.ok(
                legalRefService.getAllByType(EntityType.valueOf(type)).stream()
                        .map(LegalReferenceApiController::toDto)
                        .collect(Collectors.toList()));
    }

    @lombok.Data @lombok.Builder
    static class LegalRefResponse {
        private Long id;
        private String entityType;
        private Long entityId;
        private String title;
        private String url;
        private String article;
    }

    private static LegalRefResponse toDto(ru.bizsupport.entity.LegalReference r) {
        return LegalRefResponse.builder()
                .id(r.getId())
                .entityType(r.getEntityType().name())
                .entityId(r.getEntityId())
                .title(r.getTitle())
                .url(r.getUrl())
                .article(r.getArticle())
                .build();
    }
}
