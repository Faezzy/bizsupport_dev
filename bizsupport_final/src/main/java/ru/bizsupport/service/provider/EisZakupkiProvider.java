package ru.bizsupport.service.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ru.bizsupport.entity.Tender;
import ru.bizsupport.entity.TenderLawType;
import ru.bizsupport.entity.TenderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Провайдер данных тендеров из ЕИС (zakupki.gov.ru).
 *
 * Использует публичный поисковый API ЕИС:
 *   https://zakupki.gov.ru/api/search/searchServlet
 *
 * API не требует регистрации, но имеет rate-limit.
 * Для продакшна — зарегистрируйтесь на zakupki.gov.ru
 * и укажите app.eis.api-token в application.properties.
 *
 * Активируется через: app.tenders.provider=eis
 */
@Component
@Slf4j
public class EisZakupkiProvider implements TenderProvider {

    /** Публичный API ЕИС (SearchServlet), возвращает JSON в формате DataTables */
    private static final String SEARCH_PATH = "/api/search/searchServlet";

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    };

    @Value("${app.eis.base-url:https://zakupki.gov.ru}")
    private String baseUrl;

    /** Токен для повышенного лимита — получить на zakupki.gov.ru → Личный кабинет → API */
    @Value("${app.eis.api-token:}")
    private String apiToken;

    @Value("${app.eis.fz44:true}")
    private boolean fz44;

    @Value("${app.eis.fz223:true}")
    private boolean fz223;

    /** Количество тендеров на странице (10, 25, 50) */
    @Value("${app.eis.page-size:50}")
    private int pageSize;

    /** Сколько страниц загружать при первоначальной загрузке */
    @Value("${app.eis.initial-pages:2}")
    private int initialPages;

    @Value("${app.eis.connect-timeout-ms:15000}")
    private int connectTimeout;

    @Value("${app.eis.read-timeout-ms:45000}")
    private int readTimeout;

    /** Только МСП-закупки */
    @Value("${app.eis.msp-only:false}")
    private boolean mspOnly;

    /** Фильтр по региону (пусто = все регионы) */
    @Value("${app.eis.region:}")
    private String regionFilter;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getName() { return "eis"; }

    @Override
    public boolean supportsRefresh() { return true; }

    @Override
    public List<Tender> fetchInitial() {
        log.info("EIS: fetching initial tenders ({} pages × {} per page)", initialPages, pageSize);
        List<Tender> result = new ArrayList<>();
        for (int page = 1; page <= initialPages; page++) {
            List<Tender> page_data = fetchPage(page);
            result.addAll(page_data);
            if (page_data.isEmpty()) break;
            if (page < initialPages) sleepMs(1500); // вежливая пауза
        }
        log.info("EIS: loaded {} tenders from {} pages", result.size(), initialPages);
        return result;
    }

    @Override
    public List<Tender> fetchUpdates() {
        log.info("EIS: fetching updates (page 1)");
        return fetchPage(1);
    }

    // ─── HTTP ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Tender> fetchPage(int pageNumber) {
        try {
            RestTemplate rt = buildRestTemplate();
            HttpEntity<Void> request = buildRequest();
            String url = buildSearchUrl(pageNumber);

            log.debug("EIS request: GET {}", url);
            ResponseEntity<Map> response = rt.exchange(url, HttpMethod.GET, request, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("EIS returned non-2xx or empty body: {}", response.getStatusCode());
                return List.of();
            }

            return parseDataTablesResponse(response.getBody());

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("EIS HTTP client error {}: {}", e.getStatusCode(), e.getResponseBodyAsString().substring(0, Math.min(300, e.getResponseBodyAsString().length())));
            return List.of();
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            log.error("EIS HTTP server error {}: {}", e.getStatusCode(), e.getMessage());
            return List.of();
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("EIS connection failed (timeout or network): {}", e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.error("EIS unexpected error: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private String buildSearchUrl(int pageNumber) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromHttpUrl(baseUrl + SEARCH_PATH)
                .queryParam("morphology", "on")
                .queryParam("search-filter", "Дата размещения")
                .queryParam("pageNumber", pageNumber)
                .queryParam("sortDirection", "false")
                .queryParam("recordsPerPage", "_" + pageSize)
                .queryParam("showLotsInfoHidden", "false")
                .queryParam("sortBy", "UPDATE_DATE")
                .queryParam("currencyIdGeneral", "-1");

        if (fz44)  b.queryParam("fz44", "on");
        if (fz223) b.queryParam("fz223", "on");
        // Статусы: af=Приём заявок, ca=Уточнение, pc=Корректировка, pa=Принята
        b.queryParam("af", "on").queryParam("ca", "on").queryParam("pc", "on").queryParam("pa", "on");
        if (mspOnly) b.queryParam("isMSP", "true");
        if (regionFilter != null && !regionFilter.isBlank()) b.queryParam("regionCode", regionFilter);

        return b.build(false).toUriString();
    }

    private HttpEntity<Void> buildRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "BizSupport/1.0 (https://github.com/bizsupport; tender-integration)");
        headers.set("Referer", baseUrl + "/");
        headers.set("X-Requested-With", "XMLHttpRequest");
        if (apiToken != null && !apiToken.isBlank()) {
            headers.setBearerAuth(apiToken);
        }
        return new HttpEntity<>(headers);
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }

    // ─── Response parsing ────────────────────────────────────────────────────

    /**
     * Парсит ответ в формате DataTables:
     * { "draw": 1, "recordsFiltered": 12345, "data": [...] }
     *
     * Также обрабатывает нестандартные форматы ЕИС:
     * { "data": { "contracts": [...] } }
     * { "items": [...] }
     */
    @SuppressWarnings("unchecked")
    private List<Tender> parseDataTablesResponse(Map<String, Object> body) {
        List<Map<String, Object>> items = null;

        // Вариант 1: DataTables { "data": [...] }
        Object dataRaw = body.get("data");
        if (dataRaw instanceof List<?> list) {
            items = (List<Map<String, Object>>) list;
        }
        // Вариант 2: { "data": { "contracts": [...] } }
        else if (dataRaw instanceof Map<?, ?> dataMap) {
            for (String key : List.of("contracts", "items", "orders", "procurements", "results")) {
                Object nested = ((Map<String, Object>) dataMap).get(key);
                if (nested instanceof List<?> nestedList) {
                    items = (List<Map<String, Object>>) nestedList;
                    break;
                }
            }
        }
        // Вариант 3: { "items": [...] }
        if (items == null) {
            for (String key : List.of("items", "orders", "results", "procurements")) {
                Object raw = body.get(key);
                if (raw instanceof List<?> list) {
                    items = (List<Map<String, Object>>) list;
                    break;
                }
            }
        }

        if (items == null || items.isEmpty()) {
            log.warn("EIS: no items found in response. Top-level keys: {}", body.keySet());
            return List.of();
        }

        log.debug("EIS: parsing {} items", items.size());
        return items.stream()
                .map(this::mapToTender)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private Tender mapToTender(Map<String, Object> item) {
        try {
            String regNum = str(item, "regNum", "registryNumber", "orderNum", "noticeNumber", "number");
            if (regNum == null || regNum.isBlank()) {
                log.debug("EIS: skipping item without registry number: {}", item.keySet());
                return null;
            }

            String title = str(item, "procedureName", "name", "title", "subject", "purchaseName");
            if (title == null) title = "Закупка " + regNum;

            // Цена
            BigDecimal price = decimal(item, "maxContractPrice", "priceRur", "price", "nmck", "initialPrice");

            // Заказчик (может быть вложен или на верхнем уровне)
            String customerName = null;
            String customerInn  = null;
            Object customerRaw = item.get("customer");
            if (customerRaw instanceof Map<?, ?> cMap) {
                customerName = str((Map<String, Object>) cMap, "fullName", "name", "shortName", "title");
                customerInn  = str((Map<String, Object>) cMap, "inn", "INN", "ИНН");
            }
            if (customerName == null) customerName = str(item, "customerFullName", "customerName", "organizationName");
            if (customerInn  == null) customerInn  = str(item, "customerInn", "customerINN", "inn");
            if (customerName == null) customerName = "Неизвестный заказчик";

            // Закон
            TenderLawType lawType = parseLaw(str(item, "purchaseLaw", "law", "fzType", "legislationRf", "законType"));

            // Даты
            LocalDateTime publishedAt = parseDateTime(str(item, "publishDate", "publicationDate", "createDate", "modifyDate"));
            LocalDateTime deadline    = parseDateTime(str(item, "endDate", "submissionCloseDateTime", "biddingEndDate", "applicationEndDate"));
            LocalDate auctionDate     = parseDate(str(item, "auctionDate", "biddingDate", "openingDate"));

            // Статус
            TenderStatus status = parseStatus(str(item, "status", "statusCode", "etp", "purchaseStatus"));

            // Регион
            String region = null;
            Object regionRaw = item.get("region");
            if (regionRaw instanceof Map<?, ?> rMap) {
                region = str((Map<String, Object>) rMap, "name", "regionName", "fullName");
            }
            if (region == null) region = str(item, "regionName", "region", "placeName");

            // ОКПД2
            String okpd = str(item, "okpd2Code", "okpd2", "okpd2CodeName", "okpdCode");
            if (okpd != null && okpd.contains(" ")) okpd = okpd.split(" ")[0]; // "26.20 Компьютеры" → "26.20"

            // Обеспечение
            BigDecimal appSec      = decimal(item, "applicationGuarantee", "applicationSecurity", "securityDeposit");
            BigDecimal contractSec = decimal(item, "contractGuarantee", "contractSecurity", "executionSecurity");

            // МСП
            Boolean msp = bool(item, "isMsp", "isMSP", "mspOnly", "onlyMSP", "smallBusinessSubject");

            // Способ закупки
            String method = str(item, "procedureTypeCode", "procedureType", "purchaseType", "auctionType");
            if (method != null) method = translateProcedureCode(method);

            // Ссылка
            String href = str(item, "href", "url", "link", "sourceUrl");
            if (href != null && !href.startsWith("http")) href = baseUrl + href;

            return Tender.builder()
                    .registryNumber(regNum)
                    .title(title)
                    .description(buildDescription(item))
                    .customerName(customerName)
                    .customerInn(customerInn)
                    .lawType(lawType)
                    .procurementMethod(method)
                    .initialPrice(price)
                    .currency("RUB")
                    .region(region)
                    .okpdCode(okpd)
                    .category(deriveCategory(okpd, title))
                    .publishedAt(publishedAt)
                    .submissionDeadline(deadline)
                    .auctionDate(auctionDate)
                    .applicationSecurity(appSec)
                    .contractSecurity(contractSec)
                    .mspOnly(msp != null ? msp : false)
                    .status(status)
                    .sourceUrl(href)
                    .source("eis")
                    .build();

        } catch (Exception e) {
            log.warn("EIS: failed to map tender item: {}", e.getMessage());
            return null;
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String str(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v instanceof String s && !s.isBlank()) return s.trim();
        }
        return null;
    }

    private BigDecimal decimal(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v == null) continue;
            try {
                if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
                String s = v.toString().replace(",", ".").replaceAll("[^0-9.]", "");
                if (!s.isBlank()) return new BigDecimal(s);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private Boolean bool(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v instanceof Boolean b) return b;
            if (v instanceof String s) {
                if ("true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s)) return true;
                if ("false".equalsIgnoreCase(s) || "0".equals(s) || "no".equalsIgnoreCase(s)) return false;
            }
        }
        return null;
    }

    private LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        // Обрезать миллисекунды и часовой пояс
        raw = raw.replaceAll("\\.\\d+", "").replaceAll("[+-]\\d{2}:\\d{2}$", "").trim();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDateTime.parse(raw, fmt); } catch (DateTimeParseException ignored) {}
        }
        // Попытка как LocalDate → LocalDateTime
        try { return LocalDate.parse(raw, DateTimeFormatter.ofPattern("dd.MM.yyyy")).atStartOfDay(); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay(); } catch (DateTimeParseException ignored) {}
        log.debug("EIS: cannot parse date '{}'", raw);
        return null;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        LocalDateTime dt = parseDateTime(raw);
        return dt != null ? dt.toLocalDate() : null;
    }

    private TenderLawType parseLaw(String raw) {
        if (raw == null) return TenderLawType.FZ_44;
        if (raw.contains("223")) return TenderLawType.FZ_223;
        if (raw.contains("COMMERCIAL") || raw.contains("commercial")) return TenderLawType.COMMERCIAL;
        return TenderLawType.FZ_44;
    }

    private TenderStatus parseStatus(String raw) {
        if (raw == null) return TenderStatus.PUBLISHED;
        raw = raw.toLowerCase();
        if (raw.contains("приём") || raw.contains("подача") || raw.contains("размещена") ||
                raw.contains("published") || raw.contains("active") || raw.contains("open")) return TenderStatus.PUBLISHED;
        if (raw.contains("рассмотр") || raw.contains("review") || raw.contains("evaluation")) return TenderStatus.UNDER_REVIEW;
        if (raw.contains("торги") || raw.contains("аукцион") || raw.contains("auction")) return TenderStatus.AUCTION;
        if (raw.contains("завершен") || raw.contains("исполн") || raw.contains("completed") || raw.contains("awarded")) return TenderStatus.COMPLETED;
        if (raw.contains("отмен") || raw.contains("cancelled") || raw.contains("canceled")) return TenderStatus.CANCELLED;
        return TenderStatus.PUBLISHED;
    }

    private String translateProcedureCode(String code) {
        if (code == null) return null;
        return switch (code.toUpperCase()) {
            case "EA", "EA44" -> "Электронный аукцион";
            case "ZK", "ZK44" -> "Запрос котировок";
            case "KO", "ZKO" -> "Открытый конкурс";
            case "EP", "EP44" -> "Электронный конкурс";
            case "PO", "ZPO" -> "Запрос предложений";
            case "OP", "OKND" -> "Открытый конкурс НД";
            default -> code.length() < 50 ? code : null;
        };
    }

    private String deriveCategory(String okpd, String title) {
        if (okpd != null) {
            String prefix = okpd.length() >= 2 ? okpd.substring(0, 2) : okpd;
            return switch (prefix) {
                case "41", "42", "43" -> "Строительство и ремонт";
                case "21", "86", "87" -> "Медицина и фарма";
                case "62", "63", "58" -> "IT и связь";
                case "49", "50", "51" -> "Транспорт";
                case "17", "18"       -> "Канцтовары и поставки";
                case "81"             -> "Услуги клининга";
                case "80"             -> "Охрана";
                case "56"             -> "Питание";
                case "85"             -> "Образование и обучение";
                case "26", "27", "28" -> "Оборудование";
                default -> null;
            };
        }
        if (title != null) {
            String t = title.toLowerCase();
            if (t.contains("строит") || t.contains("ремонт")) return "Строительство и ремонт";
            if (t.contains("медиц") || t.contains("лекарст") || t.contains("фарм")) return "Медицина и фарма";
            if (t.contains("it") || t.contains("программ") || t.contains("сервер")) return "IT и связь";
            if (t.contains("транспорт") || t.contains("перевоз")) return "Транспорт";
            if (t.contains("питани") || t.contains("продукт")) return "Питание";
            if (t.contains("охран")) return "Охрана";
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String buildDescription(Map<String, Object> item) {
        String desc = str(item, "description", "subject", "lotDescription", "briefDescription");
        if (desc != null && desc.length() > 20) return desc;
        // Собираем из доступных полей
        StringBuilder sb = new StringBuilder();
        String title = str(item, "procedureName", "name", "title");
        String customer = str(item, "customerFullName", "customerName");
        if (title != null) sb.append(title).append(". ");
        if (customer != null) sb.append("Заказчик: ").append(customer).append(". ");
        sb.append("Данные получены из Единой информационной системы zakupki.gov.ru.");
        return sb.toString();
    }

    private void sleepMs(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
