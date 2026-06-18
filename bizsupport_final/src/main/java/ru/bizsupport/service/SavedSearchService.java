package ru.bizsupport.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bizsupport.dto.TenderDtos.FilterRequest;
import ru.bizsupport.dto.TenderDtos.TenderCard;
import ru.bizsupport.entity.SavedSearch;
import ru.bizsupport.entity.TenderLawType;
import ru.bizsupport.entity.User;
import ru.bizsupport.repository.SavedSearchRepository;
import ru.bizsupport.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SavedSearchService {

    private final SavedSearchRepository repo;
    private final TenderService tenderService;
    private final NotificationService notificationService;
    private final UserRepository userRepo;

    public List<SavedSearch> getUserSearches(Long userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public SavedSearch save(Long userId, String name, String query, String lawType,
                            BigDecimal priceFrom, BigDecimal priceTo, String region, Boolean mspOnly) {
        User user = userRepo.findById(userId).orElseThrow();
        // Ensure unique name per user
        String finalName = name;
        if (repo.existsByUserIdAndName(userId, finalName)) {
            finalName = name + " (" + System.currentTimeMillis() % 10000 + ")";
        }
        return repo.save(SavedSearch.builder()
                .user(user)
                .name(finalName)
                .query(query)
                .lawType(lawType)
                .priceFrom(priceFrom)
                .priceTo(priceTo)
                .region(region)
                .mspOnly(Boolean.TRUE.equals(mspOnly))
                .build());
    }

    @Transactional
    public void delete(Long id, Long userId) {
        repo.findByIdAndUserId(id, userId).ifPresent(repo::delete);
    }

    /** Выполнить сохранённый поиск, вернуть до 10 тендеров. */
    public List<TenderCard> runSearch(Long id) {
        SavedSearch ss = repo.findById(id).orElseThrow();
        return tenderService.search(buildFilter(ss, 10)).getItems();
    }

    /** Проверить все сохранённые поиски на новые совпадения и уведомить пользователей. */
    @Scheduled(cron = "${app.saved-searches.check-cron:0 0 8 * * *}")
    @Transactional
    public void checkNewMatches() {
        log.info("Проверка новых совпадений по сохранённым поискам...");
        List<SavedSearch> all = repo.findAll();
        int notified = 0;
        for (SavedSearch ss : all) {
            try {
                long count = tenderService.search(buildFilter(ss, 1)).getTotalItems();
                int prev = ss.getLastResultCount();
                if (prev == -1 && count > 0) {
                    // Первая проверка — уведомить, что есть совпадения
                    notificationService.createInfo(
                            ss.getUser(),
                            "Поиск «" + ss.getName() + "»",
                            "По вашему сохранённому поиску найдено " + count + " тендеров.");
                    notified++;
                } else if (prev >= 0 && count > prev) {
                    long newCount = count - prev;
                    notificationService.createInfo(
                            ss.getUser(),
                            "Новые тендеры по поиску «" + ss.getName() + "»",
                            "Появилось " + newCount + " новых тендеров по вашему сохранённому поиску.");
                    notified++;
                }
                ss.setLastResultCount((int) Math.min(count, Integer.MAX_VALUE));
                ss.setLastCheckedAt(LocalDateTime.now());
                repo.save(ss);
            } catch (Exception e) {
                log.warn("Ошибка проверки сохранённого поиска id={}: {}", ss.getId(), e.getMessage());
            }
        }
        log.info("Проверка завершена: {} уведомлений создано из {} поисков.", notified, all.size());
    }

    private FilterRequest buildFilter(SavedSearch ss, int size) {
        FilterRequest f = new FilterRequest();
        f.setQuery(ss.getQuery());
        if (ss.getLawType() != null && !ss.getLawType().isBlank()) {
            try { f.setLawType(TenderLawType.valueOf(ss.getLawType())); } catch (Exception ignored) {}
        }
        f.setPriceFrom(ss.getPriceFrom());
        f.setPriceTo(ss.getPriceTo());
        f.setRegion(ss.getRegion());
        f.setMspOnly(ss.getMspOnly());
        f.setSize(size);
        return f;
    }
}
