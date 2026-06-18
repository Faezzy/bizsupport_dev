package ru.bizsupport.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bizsupport.dto.TenderDtos.TenderCard;
import ru.bizsupport.dto.TenderDtos.TenderDetail;
import ru.bizsupport.dto.TenderDtos.FilterRequest;
import ru.bizsupport.entity.CompanyProfile;
import ru.bizsupport.entity.Tender;
import ru.bizsupport.entity.TenderAnalysisCache;
import ru.bizsupport.repository.TenderAnalysisCacheRepository;
import ru.bizsupport.repository.TenderRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenderAnalysisService {

    private final TenderService tenderService;
    private final TenderRepository tenderRepository;
    private final TenderAnalysisCacheRepository cacheRepo;
    private final AssistantService assistantService;
    private final CompanyProfileService companyProfileService;

    /**
     * Анализ одного тендера с кэшированием результата.
     * При повторном запросе возвращает кэш, если он существует.
     */
    public String analyzeTender(Long tenderId) {
        return analyzeTender(tenderId, false);
    }

    /**
     * Анализ одного тендера.
     * @param forceRefresh если true — игнорировать кэш и сделать новый запрос к LLM.
     */
    @Transactional
    public String analyzeTender(Long tenderId, boolean forceRefresh) {
        if (!forceRefresh) {
            var cached = cacheRepo.findTopByTenderIdOrderByCreatedAtDesc(tenderId);
            if (cached.isPresent()) {
                log.debug("Returning cached analysis for tender {}", tenderId);
                return cached.get().getAnalysis();
            }
        }

        TenderDetail detail = tenderService.getDetail(tenderId);
        String prompt = buildSingleTenderPrompt(detail);
        String analysis = assistantService.chat(List.of(), prompt);

        // Не кэшируем ошибки ассистента (нет ключа, 429, таймаут) — иначе ошибка
        // останется навсегда и кнопка «Обновить» не сможет переанализировать.
        if (!isErrorResponse(analysis)) {
            Tender tender = tenderRepository.findById(tenderId)
                    .orElseThrow(() -> new IllegalArgumentException("Tender not found: " + tenderId));
            // При повторном анализе удаляем старые записи, чтобы кэш не разрастался
            cacheRepo.deleteByTenderId(tenderId);
            cacheRepo.save(TenderAnalysisCache.builder()
                    .tender(tender)
                    .model(assistantService.getModel())
                    .analysis(analysis)
                    .build());
        }

        return analysis;
    }

    /**
     * AI-матчер: оценить, насколько текущий список тендеров подходит предпринимателю
     * по заданному профилю (категория, регион, бюджет, тип закона).
     */
    public String matchTenders(String companyContext, List<Long> tenderIds) {
        List<TenderDetail> tenders = loadDetails(tenderIds);
        if (tenders.isEmpty()) {
            return "Не удалось загрузить тендеры по указанным ID. Проверьте, что тендеры существуют.";
        }
        String prompt = buildMatcherPrompt(companyContext, tenders);
        return assistantService.chat(List.of(), prompt);
    }

    /**
     * Пакетный анализ списка тендеров: краткое сводное заключение.
     */
    public String analyzeList(List<Long> tenderIds) {
        List<TenderDetail> tenders = loadDetails(tenderIds);
        if (tenders.isEmpty()) {
            return "Не удалось загрузить тендеры по указанным ID. Проверьте, что тендеры существуют.";
        }
        String prompt = buildListAnalysisPrompt(tenders);
        return assistantService.chat(List.of(), prompt);
    }

    /**
     * Безопасно загружает детали тендеров: пропускает несуществующие ID,
     * чтобы один неверный ID не уронил весь анализ.
     */
    private List<TenderDetail> loadDetails(List<Long> tenderIds) {
        return tenderIds.stream()
                .limit(10)
                .map(id -> {
                    try {
                        return tenderService.getDetail(id);
                    } catch (Exception e) {
                        log.warn("Skipping tender {} in batch analysis: {}", id, e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** Признак того, что ответ ассистента — служебное сообщение об ошибке. */
    public static boolean isErrorResponse(String s) {
        if (s == null || s.isBlank()) return true;
        String t = s.stripLeading();
        return t.startsWith("⚠️") || t.startsWith("⚙️");
    }

    /**
     * Матчер по текущей ленте тендеров (по умолчанию — первая страница).
     * Используется из кнопки "Подобрать под мой профиль".
     */
    public String matchCurrentFeed(String companyContext) {
        FilterRequest filter = new FilterRequest();
        filter.setSize(10);
        List<Long> ids = tenderService.search(filter)
                .getItems().stream()
                .map(TenderCard::getId)
                .collect(Collectors.toList());
        return matchTenders(companyContext, ids);
    }

    /**
     * AI-матчер на основе профиля компании пользователя.
     * Автоматически собирает контекст из CompanyProfile — без ручного ввода.
     */
    public String matchByUserProfile(Long userId) {
        Optional<CompanyProfile> profileOpt = companyProfileService.findByUserId(userId);
        if (profileOpt.isEmpty()) {
            return "⚙️ Профиль компании не заполнен.\n\nЗаполните профиль (тип, отрасль, выручка), " +
                   "и AI подберёт тендеры под вашу компанию автоматически.";
        }
        return matchCurrentFeed(buildCompanyContext(profileOpt.get()));
    }

    /** Собирает текстовое описание компании из профиля (только скалярные поля — без ленивых связей). */
    private String buildCompanyContext(CompanyProfile p) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.getCompanyType() != null ? p.getCompanyType().getDisplayName() : "Компания");
        if (p.getCompanyName() != null) sb.append(" \"").append(p.getCompanyName()).append("\"");
        if (p.getIndustry() != null && !p.getIndustry().isBlank())
            sb.append(", отрасль: ").append(p.getIndustry());
        if (p.getEmployeesCount() != null)
            sb.append(", сотрудников: ").append(p.getEmployeesCount());
        if (p.getAnnualRevenue() != null)
            sb.append(", годовая выручка: ").append(fmtPrice(p.getAnnualRevenue())).append(" ₽");
        sb.append(", статус МСП: ").append(Boolean.TRUE.equals(p.getIsMsp()) ? "да" : "нет");
        sb.append(". Подбери тендеры, наиболее подходящие под профиль и масштаб этой компании.");
        return sb.toString();
    }

    // ─── Prompt builders ────────────────────────────────────────────────────

    private String buildSingleTenderPrompt(TenderDetail t) {
        return """
                Проведи детальный анализ тендера для предпринимателя МСП:

                📋 Реестровый номер: %s
                📌 Название: %s
                📝 Описание: %s
                🏛 Заказчик: %s (ИНН: %s)
                ⚖️ Закон: %s | Способ: %s
                💰 Начальная цена: %s ₽
                📍 Регион: %s | Категория: %s | ОКПД2: %s
                🔒 Обеспечение заявки: %s ₽ | Обеспечение контракта: %s ₽
                📅 Дедлайн подачи: %s (осталось %d дн.)
                🏷 МСП-квота: %s

                Дай ответ по структуре:
                1. Краткая суть тендера (2–3 предложения)
                2. Риски участия (топ-3, со ссылками на 44-ФЗ/223-ФЗ если нужно)
                3. Рекомендации по подготовке заявки
                4. Итоговая оценка привлекательности: 🟢 / 🟡 / 🔴 + обоснование
                """.formatted(
                t.getRegistryNumber(), t.getTitle(),
                t.getDescription() != null ? t.getDescription() : "—",
                t.getCustomerName(), nvl(t.getCustomerInn()),
                t.getLawTypeDisplay(), nvl(t.getProcurementMethod()),
                fmtPrice(t.getInitialPrice()),
                nvl(t.getRegion()), nvl(t.getCategory()), nvl(t.getOkpdCode()),
                fmtPrice(t.getApplicationSecurity()),
                fmtPrice(t.getContractSecurity()),
                t.getSubmissionDeadline() != null ? t.getSubmissionDeadline().toString() : "—",
                t.getDaysToDeadline(),
                Boolean.TRUE.equals(t.getMspOnly()) ? "Да (только МСП)" : "Нет"
        );
    }

    private String buildMatcherPrompt(String companyContext, List<TenderDetail> tenders) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ты — AI-матчер тендеров для МСП. Профиль предпринимателя:\n");
        sb.append(companyContext).append("\n\n");
        sb.append("Доступные тендеры:\n");
        for (int i = 0; i < tenders.size(); i++) {
            TenderDetail t = tenders.get(i);
            sb.append(String.format(
                    "%d. [ID:%d] %s | %s | %s ₽ | %s | МСП: %s\n",
                    i + 1, t.getId(), t.getTitle(), t.getLawTypeDisplay(),
                    fmtPrice(t.getInitialPrice()), nvl(t.getCategory()),
                    Boolean.TRUE.equals(t.getMspOnly()) ? "да" : "нет"
            ));
        }
        sb.append("""

                Задача: определи, какие тендеры лучше всего подходят этому предпринимателю.
                Формат ответа:
                1. Список подходящих тендеров (ID + краткое обоснование, почему подходит)
                2. Тендеры, которые не рекомендуются (ID + причина)
                3. Совет: на что обратить внимание при подаче заявок
                """);
        return sb.toString();
    }

    private String buildListAnalysisPrompt(List<TenderDetail> tenders) {
        StringBuilder sb = new StringBuilder();
        sb.append("Проанализируй список тендеров и дай сводное заключение:\n\n");
        for (int i = 0; i < tenders.size(); i++) {
            TenderDetail t = tenders.get(i);
            sb.append(String.format(
                    "%d. [ID:%d] %s\n   Заказчик: %s | %s | Цена: %s ₽ | Регион: %s | МСП: %s | Дедлайн: %d дн.\n\n",
                    i + 1, t.getId(), t.getTitle(), t.getCustomerName(),
                    t.getLawTypeDisplay(), fmtPrice(t.getInitialPrice()),
                    nvl(t.getRegion()), Boolean.TRUE.equals(t.getMspOnly()) ? "да" : "нет",
                    t.getDaysToDeadline()
            ));
        }
        sb.append("""
                Дай сводный анализ:
                1. Общая картина рынка (объём, законы, регионы)
                2. Топ-3 наиболее привлекательных тендера с обоснованием
                3. Общие риски и тренды в этой подборке
                4. Практические советы для МСП, желающих участвовать
                """);
        return sb.toString();
    }

    public String getAssistantModel() {
        return assistantService.getModel();
    }

    private String nvl(String s) {
        return s != null ? s : "—";
    }

    private String fmtPrice(java.math.BigDecimal v) {
        if (v == null) return "—";
        return String.format("%,.0f", v);
    }
}
