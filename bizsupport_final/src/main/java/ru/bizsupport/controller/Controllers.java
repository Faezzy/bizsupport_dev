package ru.bizsupport.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import ru.bizsupport.dto.request.*;
import ru.bizsupport.entity.*;
import ru.bizsupport.repository.UserRepository;
import ru.bizsupport.service.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// ── AUTH ──────────────────────────────────────────────────────
@Controller
class AuthController {
    private final AuthService authService;
    AuthController(AuthService a) { this.authService = a; }

    @GetMapping("/login")
    String loginPage() { return "auth/login"; }

    @GetMapping("/register")
    String registerPage(Model m) {
        m.addAttribute("req", new RegisterRequest());
        return "auth/register";
    }

    @PostMapping("/register")
    String register(@Valid @ModelAttribute("req") RegisterRequest req,
                    BindingResult br, Model m) {
        if (br.hasErrors()) return "auth/register";
        try {
            authService.register(req);
            return "redirect:/login?registered=true";
        } catch (IllegalArgumentException e) {
            m.addAttribute("error", e.getMessage());
            return "auth/register";
        }
    }
}

// ── DASHBOARD ─────────────────────────────────────────────────
@Controller
@RequiredArgsConstructor
class DashboardController {
    private final UserRepository userRepo;
    private final CompanyProfileService profileService;
    private final TaxService taxService;
    private final ProcurementService procurementService;
    private final TenderService tenderService;

    @GetMapping({"/", "/dashboard"})
    String dashboard(@AuthenticationPrincipal UserDetails ud, Model m) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        m.addAttribute("user", user);
        profileService.findByUserId(user.getId()).ifPresent(profile -> {
            m.addAttribute("profile", profile);
            m.addAttribute("currentRegimes", taxService.getCompanyCurrentRegimes(profile.getId()));
            m.addAttribute("checklists", procurementService.getUserChecklists(user.getId()));
        });
        // Виджет: ближайшие по дедлайну открытые тендеры
        m.addAttribute("upcomingTenders", tenderService.getUpcoming(5));
        return "dashboard/index";
    }
}

// ── PROFILE ───────────────────────────────────────────────────
@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
class ProfileController {
    private final UserRepository userRepo;
    private final CompanyProfileService profileService;

    @GetMapping
    String profilePage(@AuthenticationPrincipal UserDetails ud, Model m) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        CompanyProfileRequest req = new CompanyProfileRequest();
        profileService.findByUserId(user.getId()).ifPresent(p -> {
            req.setCompanyName(p.getCompanyName());
            req.setCompanyType(p.getCompanyType());
            req.setInn(p.getInn());
            req.setIndustry(p.getIndustry());
            req.setEmployeesCount(p.getEmployeesCount());
            req.setAnnualRevenue(p.getAnnualRevenue());
        });
        m.addAttribute("req", req);
        m.addAttribute("companyTypes", CompanyProfile.CompanyType.values());
        m.addAttribute("user", user);
        return "profile/edit";
    }

    @PostMapping
    String saveProfile(@AuthenticationPrincipal UserDetails ud,
                       @Valid @ModelAttribute("req") CompanyProfileRequest req,
                       BindingResult br, Model m) {
        if (br.hasErrors()) {
            m.addAttribute("companyTypes", CompanyProfile.CompanyType.values());
            return "profile/edit";
        }
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        profileService.saveOrUpdate(user.getId(), req);
        return "redirect:/dashboard?profileSaved=true";
    }
}

// ── TAX ───────────────────────────────────────────────────────
@Controller
@RequestMapping("/tax")
@RequiredArgsConstructor
class TaxController {
    private final UserRepository userRepo;
    private final CompanyProfileService profileService;
    private final TaxService taxService;
    private final FavoriteService favoriteService;
    private final LegalReferenceService legalRefService;
    private final TaxCalculatorService calculatorService;

    @GetMapping
    String taxMain(@AuthenticationPrincipal UserDetails ud, Model m) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        m.addAttribute("allRegimes", taxService.getAllRegimes());
        profileService.findByUserId(user.getId()).ifPresent(p -> {
            m.addAttribute("profile", p);
            m.addAttribute("currentRegimes", taxService.getCompanyCurrentRegimes(p.getId()));
        });
        return "tax/index";
    }

    @GetMapping("/regime/{code}")
    String regimeDetail(@PathVariable String code,
                        @AuthenticationPrincipal UserDetails ud, Model m) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        TaxRegime regime = taxService.findByCode(code).orElseThrow();
        m.addAttribute("regime", regime);
        m.addAttribute("obligations", taxService.getObligationsForRegime(regime.getId()));
        m.addAttribute("deadlines", taxService.getTemplateDeadlines(regime.getId()));
        m.addAttribute("legalRefs", legalRefService.getReferences(EntityType.TAX_REGIME, regime.getId()));
        m.addAttribute("isFavorite", favoriteService.isFavorite(user.getId(), EntityType.TAX_REGIME, regime.getId()));
        return "tax/regime";
    }

    @PostMapping("/regime/{code}/favorite")
    String toggleFavorite(@PathVariable String code, @AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        TaxRegime regime = taxService.findByCode(code).orElseThrow();
        favoriteService.toggle(user.getId(), EntityType.TAX_REGIME, regime.getId());
        return "redirect:/tax/regime/" + code;
    }

    @GetMapping("/recommend")
    String recommend(@RequestParam(required = false) String type,
                     @RequestParam(required = false) Integer employees,
                     @RequestParam(required = false) BigDecimal revenue,
                     Model m) {
        if (type != null) {
            CompanyProfile.CompanyType ct = CompanyProfile.CompanyType.valueOf(type);
            m.addAttribute("recommended", taxService.recommend(ct, employees, revenue));
            m.addAttribute("type", type);
            m.addAttribute("employees", employees);
            m.addAttribute("revenue", revenue);
        }
        m.addAttribute("companyTypes", CompanyProfile.CompanyType.values());
        return "tax/recommend";
    }

    @PostMapping("/set-regime")
    String setRegime(@AuthenticationPrincipal UserDetails ud, @RequestParam String code) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        profileService.findByUserId(user.getId()).ifPresent(p ->
            profileService.setTaxRegime(p.getId(), code));
        return "redirect:/tax?regimeSet=true";
    }

    @GetMapping("/calculator")
    String calculator(
            @RequestParam(required = false) BigDecimal revenue,
            @RequestParam(required = false) BigDecimal expenses,
            @RequestParam(required = false, defaultValue = "0") int employees,
            @RequestParam(required = false) BigDecimal avgSalary,
            @RequestParam(required = false, defaultValue = "true") boolean ip,
            Model m) {

        // Всегда передаём значения в модель (для формы)
        m.addAttribute("revenue", revenue);
        m.addAttribute("expenses", expenses);
        m.addAttribute("employees", employees);
        m.addAttribute("avgSalary", avgSalary);
        m.addAttribute("ip", ip);

        if (revenue != null) {
            var input = TaxCalculatorService.TaxCalcInput.builder()
                    .revenue(revenue)
                    .expenses(expenses != null ? expenses : BigDecimal.ZERO)
                    .employees(employees)
                    .avgSalary(avgSalary != null ? avgSalary : BigDecimal.ZERO)
                    .ip(ip)
                    .build();
            m.addAttribute("results", calculatorService.calculate(input));
        }
        return "tax/calculator";
    }
}

// ── PROCUREMENT ───────────────────────────────────────────────
@Controller
@RequestMapping("/procurement")
@RequiredArgsConstructor
class ProcurementController {
    private final UserRepository userRepo;
    private final CompanyProfileService profileService;
    private final ProcurementService procurementService;
    private final FavoriteService favoriteService;
    private final LegalReferenceService legalRefService;

    @GetMapping
    String procMain(@AuthenticationPrincipal UserDetails ud, Model m) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        boolean isMsp = profileService.findByUserId(user.getId())
                .map(CompanyProfile::getIsMsp).orElse(false);
        m.addAttribute("scenarios", isMsp
                ? procurementService.getMspScenarios()
                : procurementService.getAllScenarios());
        m.addAttribute("isMsp", isMsp);
        return "procurement/index";
    }

    @GetMapping("/scenario/{id}")
    String scenarioDetail(@PathVariable Long id,
                          @AuthenticationPrincipal UserDetails ud, Model m) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        ProcurementScenario scenario = procurementService.getScenarioById(id);
        m.addAttribute("scenario", scenario);
        m.addAttribute("risks", procurementService.getRisksForScenario(id));
        m.addAttribute("templates", procurementService.getTemplatesForScenario(scenario));
        m.addAttribute("legalRefs", legalRefService.getReferences(EntityType.PROCUREMENT, id));
        m.addAttribute("isFavorite", favoriteService.isFavorite(user.getId(), EntityType.PROCUREMENT, id));
        return "procurement/scenario";
    }

    @PostMapping("/scenario/{id}/favorite")
    String toggleScenarioFavorite(@PathVariable Long id, @AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        favoriteService.toggle(user.getId(), EntityType.PROCUREMENT, id);
        return "redirect:/procurement/scenario/" + id;
    }

    @GetMapping("/checklists")
    String myChecklists(@AuthenticationPrincipal UserDetails ud, Model m) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        m.addAttribute("checklists", procurementService.getUserChecklists(user.getId()));
        m.addAttribute("templates", procurementService.getTemplates());
        return "procurement/checklists";
    }

    @PostMapping("/checklist/copy/{templateId}")
    String copyTemplate(@PathVariable Long templateId, @AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        Checklist cl = procurementService.copyTemplateForUser(templateId, user.getId());
        return "redirect:/procurement/checklist/" + cl.getId();
    }

    @GetMapping("/checklist/{id}")
    String checklistDetail(@PathVariable Long id, Model m) {
        m.addAttribute("checklist", procurementService.getChecklist(id));
        return "procurement/checklist";
    }

    @PostMapping("/checklist/step/{stepId}/toggle")
    String toggleStep(@PathVariable Long stepId, @RequestParam Long checklistId) {
        procurementService.toggleStep(stepId);
        return "redirect:/procurement/checklist/" + checklistId;
    }
}

// ── SEARCH ────────────────────────────────────────────────────
@Controller
@RequiredArgsConstructor
class SearchController {
    private final TaxService taxService;
    private final ProcurementService procurementService;

    @GetMapping("/search")
    String search(@RequestParam(required = false) String q, Model m) {
        m.addAttribute("query", q);
        if (q != null && !q.isBlank()) {
            String lq = q.toLowerCase();
            List<SearchResult> results = new ArrayList<>();
            taxService.getAllRegimes().stream()
                .filter(r -> r.getName().toLowerCase().contains(lq)
                    || (r.getDescription() != null && r.getDescription().toLowerCase().contains(lq))
                    || r.getCode().toLowerCase().contains(lq))
                .forEach(r -> results.add(new SearchResult(
                    r.getName(),
                    r.getDescription() != null ? trunc(r.getDescription(), 120) : "",
                    "/tax/regime/" + r.getCode(), "TAX")));
            procurementService.getAllScenarios().stream()
                .filter(s -> s.getTitle().toLowerCase().contains(lq)
                    || (s.getDescription() != null && s.getDescription().toLowerCase().contains(lq))
                    || s.getLawType().getDisplayName().toLowerCase().contains(lq))
                .forEach(s -> results.add(new SearchResult(
                    s.getTitle(),
                    s.getDescription() != null ? trunc(s.getDescription(), 120) : "",
                    "/procurement/scenario/" + s.getId(), "PROCUREMENT")));
            m.addAttribute("results", results);
        }
        return "search/index";
    }

    private String trunc(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    public static class SearchResult {
        private final String title, snippet, url, type;
        public SearchResult(String t, String s, String u, String tp) {
            title = t; snippet = s; url = u; type = tp;
        }
        public String getTitle() { return title; }
        public String getSnippet() { return snippet; }
        public String getUrl() { return url; }
        public String getType() { return type; }
    }
}

// ── FAVORITES ─────────────────────────────────────────────────
@Controller
@RequestMapping("/favorites")
@RequiredArgsConstructor
class FavoritesPageController {
    private final UserRepository userRepo;
    private final FavoriteService favoriteService;
    private final TaxService taxService;
    private final ProcurementService procurementService;
    private final TenderService tenderService;

    @GetMapping
    String favoritesPage(@AuthenticationPrincipal UserDetails ud, Model m) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        List<UserFavorite> favorites = favoriteService.getUserFavorites(user.getId());

        // Подгружаем названия для каждого избранного
        List<java.util.Map<String, Object>> enriched = new ArrayList<>();
        for (UserFavorite fav : favorites) {
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", fav.getId());
            item.put("entityType", fav.getEntityType().name());
            item.put("entityTypeDisplay", fav.getEntityType().getDisplayName());
            item.put("entityId", fav.getEntityId());
            item.put("createdAt", fav.getCreatedAt());

            if (fav.getEntityType() == EntityType.TAX_REGIME) {
                taxService.getAllRegimes().stream()
                    .filter(r -> r.getId().equals(fav.getEntityId()))
                    .findFirst()
                    .ifPresent(r -> {
                        item.put("title", r.getName());
                        item.put("url", "/tax/regime/" + r.getCode());
                    });
            } else if (fav.getEntityType() == EntityType.PROCUREMENT) {
                try {
                    ProcurementScenario s = procurementService.getScenarioById(fav.getEntityId());
                    item.put("title", s.getTitle());
                    item.put("url", "/procurement/scenario/" + s.getId());
                } catch (Exception ignored) {}
            } else if (fav.getEntityType() == EntityType.TENDER) {
                try {
                    var t = tenderService.getDetail(fav.getEntityId());
                    item.put("title", t.getTitle());
                    item.put("url", "/tenders/" + t.getId());
                } catch (Exception ignored) {}
            }
            if (item.containsKey("title")) enriched.add(item);
        }
        m.addAttribute("favorites", enriched);
        return "favorites/index";
    }

    @PostMapping("/{id}/remove")
    String removeFavorite(@PathVariable Long id,
                          @AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        favoriteService.getUserFavorites(user.getId()).stream()
            .filter(f -> f.getId().equals(id))
            .findFirst()
            .ifPresent(f -> favoriteService.remove(user.getId(), f.getEntityType(), f.getEntityId()));
        return "redirect:/favorites";
    }
}

// ── NOTIFICATIONS ─────────────────────────────────────────────
@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
class NotificationController {
    private final UserRepository userRepo;
    private final ru.bizsupport.service.NotificationService notificationService;

    @GetMapping
    String notificationsPage(@AuthenticationPrincipal UserDetails ud, Model m) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        m.addAttribute("notifications", notificationService.getUserNotifications(user.getId()));
        m.addAttribute("unreadCount", notificationService.countUnread(user.getId()));
        return "notifications/index";
    }

    @PostMapping("/{id}/read")
    String markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return "redirect:/notifications";
    }

    @PostMapping("/read-all")
    String markAllAsRead(@AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        notificationService.markAllAsRead(user.getId());
        return "redirect:/notifications";
    }

    @PostMapping("/{id}/delete")
    String deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return "redirect:/notifications";
    }

    /** JSON-эндпоинт для AJAX-бейджа в навбаре */
    @GetMapping("/count")
    @ResponseBody
    java.util.Map<String, Long> unreadCount(@AuthenticationPrincipal UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        return java.util.Map.of("count", notificationService.countUnread(user.getId()));
    }
}

// ── ERROR HANDLER ─────────────────────────────────────────────
@Controller
class AppErrorController implements ErrorController {
    @RequestMapping("/error")
    String handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (status != null && Integer.parseInt(status.toString()) == HttpStatus.NOT_FOUND.value())
            return "error/404";
        return "error/500";
    }
}
