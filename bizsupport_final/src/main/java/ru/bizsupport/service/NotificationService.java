package ru.bizsupport.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bizsupport.entity.*;
import ru.bizsupport.repository.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepo;
    private final DeadlineRepository deadlineRepo;
    private final CompanyProfileRepository profileRepo;
    private final UserFavoriteRepository favoriteRepo;
    private final TenderRepository tenderRepo;

    // ── Чтение ────────────────────────────────────────────────

    public List<Notification> getUserNotifications(Long userId) {
        return notificationRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Notification> getUnreadNotifications(Long userId) {
        return notificationRepo.findByUserIdAndIsReadFalse(userId);
    }

    public long countUnread(Long userId) {
        return notificationRepo.countByUserIdAndIsReadFalse(userId);
    }

    // ── Отметка ───────────────────────────────────────────────

    @Transactional
    public void markAsRead(Long notificationId) {
        notificationRepo.findById(notificationId).ifPresent(n -> {
            n.setIsRead(true);
            notificationRepo.save(n);
        });
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepo.findByUserIdAndIsReadFalse(userId);
        unread.forEach(n -> n.setIsRead(true));
        notificationRepo.saveAll(unread);
    }

    @Transactional
    public void deleteNotification(Long notificationId) {
        notificationRepo.deleteById(notificationId);
    }

    // ── Генерация уведомлений ─────────────────────────────────
    // Запускается каждый день в 07:00
    // За 7, 3 и 1 день до дедлайна создаёт уведомление,
    // если аналогичное ещё не существует.

    @Scheduled(cron = "0 0 7 * * *")
    @Transactional
    public void generateDeadlineReminders() {
        log.info("Генерация напоминаний о дедлайнах...");
        LocalDate today = LocalDate.now();
        int[] remindDays = {7, 3, 1};

        List<CompanyProfile> profiles = profileRepo.findAll();
        int created = 0;

        for (CompanyProfile profile : profiles) {
            User user = profile.getUser();

            for (int daysBefore : remindDays) {
                LocalDate targetDate = today.plusDays(daysBefore);

                // Ищем дедлайны компании на эту дату
                List<Deadline> deadlines = deadlineRepo.findUpcoming(
                        profile.getId(), targetDate, targetDate);

                // Также ищем шаблонные дедлайны (без привязки к компании)
                // по режимам, которые установлены у компании
                List<Deadline> templateDeadlines = findTemplateDeadlinesForDate(
                        profile, targetDate);

                for (Deadline d : concat(deadlines, templateDeadlines)) {
                    if (!alreadyNotified(user.getId(), d.getId(), daysBefore)) {
                        createReminder(user, d, daysBefore);
                        created++;
                    }
                }
            }
        }
        log.info("Создано {} напоминаний", created);
    }

    // ── Напоминания по дедлайнам тендеров из избранного ───────
    // Запускается ежедневно в 07:30.
    // За 7, 3 и 1 день до окончания подачи заявок создаёт уведомление
    // по каждому тендеру, добавленному пользователем в избранное.

    @Scheduled(cron = "${app.notifications.tender-cron:0 30 7 * * *}")
    @Transactional
    public void generateTenderDeadlineReminders() {
        log.info("Генерация напоминаний по дедлайнам тендеров...");
        int[] remindDays = {7, 3, 1};
        LocalDate today = LocalDate.now();
        int created = 0;

        List<UserFavorite> tenderFavorites = favoriteRepo.findByEntityType(EntityType.TENDER);

        for (UserFavorite fav : tenderFavorites) {
            Tender tender = tenderRepo.findById(fav.getEntityId()).orElse(null);
            if (tender == null || tender.getSubmissionDeadline() == null) continue;

            long daysLeft = ChronoUnit.DAYS.between(today, tender.getSubmissionDeadline().toLocalDate());
            for (int daysBefore : remindDays) {
                if (daysLeft == daysBefore
                        && !alreadyNotifiedTender(fav.getUser().getId(), tender.getRegistryNumber(), daysBefore)) {
                    createTenderReminder(fav.getUser(), tender, daysBefore);
                    created++;
                }
            }
        }
        log.info("Создано {} напоминаний по тендерам", created);
    }

    private boolean alreadyNotifiedTender(Long userId, String registryNumber, int daysBefore) {
        LocalDateTime from = LocalDateTime.now().minusHours(12);
        LocalDateTime to = LocalDateTime.now().plusHours(12);
        return notificationRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .anyMatch(n -> n.getMessage() != null
                        && n.getMessage().contains(registryNumber)
                        && n.getTitle() != null
                        && n.getTitle().contains(daysBefore + " дн.")
                        && n.getCreatedAt() != null
                        && n.getCreatedAt().isAfter(from)
                        && n.getCreatedAt().isBefore(to));
    }

    private void createTenderReminder(User user, Tender tender, int daysBefore) {
        String urgency = switch (daysBefore) {
            case 1 -> "Срочно! 1 дн.";
            default -> "Через " + daysBefore + " дн.";
        };
        Notification notification = Notification.builder()
                .user(user)
                .deadline(null) // тендерные напоминания не привязаны к налоговым дедлайнам
                .title(urgency + ": подача заявок по тендеру")
                .message("Тендер «" + tender.getTitle() + "» (рег. № " + tender.getRegistryNumber()
                        + "). Приём заявок до " + tender.getSubmissionDeadline().toLocalDate()
                        + ". Не пропустите дедлайн подачи.")
                .isRead(false)
                .sendAt(LocalDateTime.now())
                .build();
        notificationRepo.save(notification);
    }

    // ── Вспомогательные методы ────────────────────────────────

    private List<Deadline> findTemplateDeadlinesForDate(CompanyProfile profile, LocalDate date) {
        // Берём шаблонные дедлайны по текущим режимам компании
        return profile.getCompanyTaxRegimes().stream()
                .filter(CompanyTaxRegime::getIsCurrent)
                .map(CompanyTaxRegime::getTaxRegime)
                .flatMap(regime -> deadlineRepo
                        .findByTaxRegimeIdAndCompanyProfileIsNull(regime.getId())
                        .stream())
                .filter(d -> d.getDueDate().equals(date))
                .toList();
    }

    private boolean alreadyNotified(Long userId, Long deadlineId, int daysBefore) {
        // Проверяем: нет ли уведомления с таким же deadlineId,
        // созданного в диапазоне ±12 часов от текущего момента
        LocalDateTime from = LocalDateTime.now().minusHours(12);
        LocalDateTime to = LocalDateTime.now().plusHours(12);

        return notificationRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .anyMatch(n -> n.getDeadline() != null
                        && n.getDeadline().getId().equals(deadlineId)
                        && n.getTitle().contains(daysBefore + " дн.")
                        && n.getCreatedAt() != null
                        && n.getCreatedAt().isAfter(from)
                        && n.getCreatedAt().isBefore(to));
    }

    private void createReminder(User user, Deadline deadline, int daysBefore) {
        String urgency = switch (daysBefore) {
            case 1 -> "Срочно! Завтра";
            case 3 -> "Через 3 дн.";
            default -> "Через " + daysBefore + " дн.";
        };

        Notification notification = Notification.builder()
                .user(user)
                .deadline(deadline)
                .title(urgency + ": " + deadline.getTitle())
                .message(deadline.getDescription() != null
                        ? deadline.getDescription()
                        : "Дедлайн: " + deadline.getDueDate())
                .isRead(false)
                .sendAt(LocalDateTime.now())
                .build();
        notificationRepo.save(notification);
    }

    private <T> List<T> concat(List<T> a, List<T> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }
}
