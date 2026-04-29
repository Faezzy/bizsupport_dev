package ru.bizsupport.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// ── Обёртка ───────────────────────────────────────────────────
public class ApiResponses {

    // ── Auth ──────────────────────────────────────────────────
    @Data @Builder
    public static class AuthResponse {
        private String token;
        private String email;
        private String fullName;
    }

    // ── User ──────────────────────────────────────────────────
    @Data @Builder
    public static class UserResponse {
        private Long id;
        private String email;
        private String fullName;
        private String role;
        private LocalDateTime createdAt;
    }

    // ── Company Profile ───────────────────────────────────────
    @Data @Builder
    public static class CompanyProfileResponse {
        private Long id;
        private String companyName;
        private String companyType;
        private String companyTypeDisplay;
        private String inn;
        private String industry;
        private Integer employeesCount;
        private BigDecimal annualRevenue;
        private Boolean isMsp;
        private List<CompanyTaxRegimeResponse> currentRegimes;
    }

    // ── Tax Regime ────────────────────────────────────────────
    @Data @Builder
    public static class TaxRegimeResponse {
        private Long id;
        private String code;
        private String name;
        private String description;
        private String conditions;
        private String nkRef;
    }

    @Data @Builder
    public static class TaxRegimeDetailResponse {
        private TaxRegimeResponse regime;
        private List<TaxObligationResponse> obligations;
        private List<DeadlineResponse> deadlines;
    }

    @Data @Builder
    public static class TaxObligationResponse {
        private Long id;
        private String taxName;
        private String rate;
        private String description;
        private String nkRef;
        private String fnsServiceUrl;
    }

    @Data @Builder
    public static class CompanyTaxRegimeResponse {
        private Long id;
        private String regimeCode;
        private String regimeName;
        private Boolean isCurrent;
        private LocalDate appliedSince;
    }

    // ── Deadline ──────────────────────────────────────────────
    @Data @Builder
    public static class DeadlineResponse {
        private Long id;
        private String title;
        private String description;
        private LocalDate dueDate;
        private String repeatRule;
        private Boolean isCustom;
    }

    // ── Procurement ───────────────────────────────────────────
    @Data @Builder
    public static class ScenarioResponse {
        private Long id;
        private String lawType;
        private String lawTypeDisplay;
        private String title;
        private String description;
        private Boolean mspOnly;
        private BigDecimal amountMin;
        private BigDecimal amountMax;
    }

    @Data @Builder
    public static class ScenarioDetailResponse {
        private ScenarioResponse scenario;
        private List<RiskCardResponse> risks;
        private List<ChecklistResponse> templates;
    }

    @Data @Builder
    public static class RiskCardResponse {
        private Long id;
        private String title;
        private String riskType;
        private String riskTypeDisplay;
        private String description;
        private String consequence;
        private String recommendation;
    }

    // ── Checklist ─────────────────────────────────────────────
    @Data @Builder
    public static class ChecklistResponse {
        private Long id;
        private String title;
        private String description;
        private Boolean isTemplate;
        private Boolean isCompleted;
        private Integer progressPercent;
        private Integer totalSteps;
        private Integer completedSteps;
        private List<ChecklistStepResponse> steps;
    }

    @Data @Builder
    public static class ChecklistStepResponse {
        private Long id;
        private Integer stepOrder;
        private String title;
        private String description;
        private String hint;
        private Boolean isCompleted;
        private LocalDateTime completedAt;
    }

    // ── Search ────────────────────────────────────────────────
    @Data @Builder
    public static class SearchResultResponse {
        private String title;
        private String snippet;
        private String url;
        private String type;
    }

    // ── Dashboard ─────────────────────────────────────────────
    @Data @Builder
    public static class DashboardResponse {
        private UserResponse user;
        private CompanyProfileResponse profile;
        private List<CompanyTaxRegimeResponse> currentRegimes;
        private List<ChecklistResponse> checklists;
    }

    // ── Generic ───────────────────────────────────────────────
    @Data @AllArgsConstructor
    public static class MessageResponse {
        private String message;
    }

    @Data @AllArgsConstructor
    public static class ErrorResponse {
        private int status;
        private String error;
        private String message;
        private LocalDateTime timestamp;

        public ErrorResponse(int status, String error, String message) {
            this.status = status;
            this.error = error;
            this.message = message;
            this.timestamp = LocalDateTime.now();
        }
    }
}
