package ru.bizsupport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.bizsupport.entity.TenderLawType;
import ru.bizsupport.entity.TenderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class TenderDtos {

    /** Параметры фильтрации тендеров */
    @Data
    @NoArgsConstructor
    public static class FilterRequest {
        private String query;            // поиск по тексту
        private TenderLawType lawType;
        private TenderStatus status;
        private String region;
        private String category;
        private BigDecimal priceFrom;
        private BigDecimal priceTo;
        private Boolean mspOnly;
        private int page = 0;
        private int size = 20;
        private String sort = "publishedAt,desc";
    }

    /** Карточка тендера для ленты (укороченная) */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TenderCard {
        private Long id;
        private String registryNumber;
        private String title;
        private String customerName;
        private String lawType;
        private String lawTypeDisplay;
        private BigDecimal initialPrice;
        private String region;
        private String category;
        private LocalDateTime submissionDeadline;
        private String status;
        private String statusDisplay;
        private String statusBadge;
        private Boolean mspOnly;
        private long daysToDeadline;
    }

    /** Полная карточка тендера */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TenderDetail {
        private Long id;
        private String registryNumber;
        private String title;
        private String description;
        private String customerName;
        private String customerInn;
        private String lawType;
        private String lawTypeDisplay;
        private String procurementMethod;
        private BigDecimal initialPrice;
        private String currency;
        private String region;
        private String okpdCode;
        private String category;
        private LocalDateTime publishedAt;
        private LocalDateTime submissionDeadline;
        private LocalDate auctionDate;
        private BigDecimal applicationSecurity;
        private BigDecimal contractSecurity;
        private Boolean mspOnly;
        private String status;
        private String statusDisplay;
        private String statusBadge;
        private String sourceUrl;
        private String source;
        private long daysToDeadline;
    }

    /** Сводная статистика */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TenderStats {
        private long total;
        private long published;
        private long underReview;
        private long completed;
    }

    /** Аналитика тендеров для дашборда */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TenderAnalytics {
        private long total;
        private long fz44Count;
        private long fz223Count;
        private long published;
        private long underReview;
        private long completed;
        private long mspOnlyCount;
        private BigDecimal avgPrice;
        private List<MonthlyCount> byMonth;

        @Data
        @AllArgsConstructor
        public static class MonthlyCount {
            private String month;
            private String label;
            private long count;
        }
    }

    /** Ответ списка с метаданными для фильтров */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TenderListResponse {
        private List<TenderCard> items;
        private long totalItems;
        private int totalPages;
        private int currentPage;
        private List<String> availableRegions;
        private List<String> availableCategories;
    }
}
