package ru.bizsupport.controller.api;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.bizsupport.dto.TenderDtos.TenderCard;
import ru.bizsupport.entity.SavedSearch;
import ru.bizsupport.entity.User;
import ru.bizsupport.repository.UserRepository;
import ru.bizsupport.service.SavedSearchService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST API сохранённых поисков.
 *   GET    /api/saved-searches           — список поисков текущего пользователя
 *   POST   /api/saved-searches           — сохранить поиск
 *   DELETE /api/saved-searches/{id}      — удалить поиск
 *   GET    /api/saved-searches/{id}/run  — выполнить поиск, вернуть тендеры
 */
@RestController
@RequestMapping("/api/saved-searches")
@RequiredArgsConstructor
public class SavedSearchApiController {

    private final SavedSearchService savedSearchService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<SavedSearch>> list(@AuthenticationPrincipal UserDetails ud) {
        User user = resolve(ud);
        return ResponseEntity.ok(savedSearchService.getUserSearches(user.getId()));
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody SaveRequest req,
                                  @AuthenticationPrincipal UserDetails ud) {
        if (req.getName() == null || req.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Название обязательно"));
        }
        User user = resolve(ud);
        SavedSearch ss = savedSearchService.save(
                user.getId(), req.getName(), req.getQuery(),
                req.getLawType(), req.getPriceFrom(), req.getPriceTo(),
                req.getRegion(), req.getMspOnly());
        return ResponseEntity.ok(Map.of("id", ss.getId(), "name", ss.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id,
                                                       @AuthenticationPrincipal UserDetails ud) {
        User user = resolve(ud);
        savedSearchService.delete(id, user.getId());
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @GetMapping("/{id}/run")
    public ResponseEntity<List<TenderCard>> run(@PathVariable Long id) {
        return ResponseEntity.ok(savedSearchService.runSearch(id));
    }

    private User resolve(UserDetails ud) {
        return userRepository.findByEmail(ud.getUsername()).orElseThrow();
    }

    @Data
    public static class SaveRequest {
        private String name;
        private String query;
        private String lawType;
        private BigDecimal priceFrom;
        private BigDecimal priceTo;
        private String region;
        private Boolean mspOnly;
    }
}
