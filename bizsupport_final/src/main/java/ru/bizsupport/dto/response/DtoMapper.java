package ru.bizsupport.dto.response;

import ru.bizsupport.entity.*;
import ru.bizsupport.dto.response.ApiResponses.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Конвертация Entity → Response DTO.
 * Статический утилитный класс — маппинг без циклических ссылок и LazyInit.
 */
public final class DtoMapper {

    private DtoMapper() {}

    // ── User ──────────────────────────────────────────────────
    public static UserResponse toDto(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt())
                .build();
    }

    // ── Company Profile ───────────────────────────────────────
    public static CompanyProfileResponse toDto(CompanyProfile p) {
        return toDto(p, Collections.emptyList());
    }

    public static CompanyProfileResponse toDto(CompanyProfile p, List<CompanyTaxRegime> regimes) {
        return CompanyProfileResponse.builder()
                .id(p.getId())
                .companyName(p.getCompanyName())
                .companyType(p.getCompanyType().name())
                .companyTypeDisplay(p.getCompanyType().getDisplayName())
                .inn(p.getInn())
                .industry(p.getIndustry())
                .employeesCount(p.getEmployeesCount())
                .annualRevenue(p.getAnnualRevenue())
                .isMsp(p.getIsMsp())
                .currentRegimes(regimes.stream().map(DtoMapper::toDto).collect(Collectors.toList()))
                .build();
    }

    // ── Tax Regime ────────────────────────────────────────────
    public static TaxRegimeResponse toDto(TaxRegime r) {
        return TaxRegimeResponse.builder()
                .id(r.getId())
                .code(r.getCode())
                .name(r.getName())
                .description(r.getDescription())
                .conditions(r.getConditions())
                .nkRef(r.getNkRef())
                .build();
    }

    public static TaxObligationResponse toDto(TaxObligation o) {
        return TaxObligationResponse.builder()
                .id(o.getId())
                .taxName(o.getTaxName())
                .rate(o.getRate())
                .description(o.getDescription())
                .nkRef(o.getNkRef())
                .fnsServiceUrl(o.getFnsServiceUrl())
                .build();
    }

    public static CompanyTaxRegimeResponse toDto(CompanyTaxRegime ctr) {
        return CompanyTaxRegimeResponse.builder()
                .id(ctr.getId())
                .regimeCode(ctr.getTaxRegime().getCode())
                .regimeName(ctr.getTaxRegime().getName())
                .isCurrent(ctr.getIsCurrent())
                .appliedSince(ctr.getAppliedSince())
                .build();
    }

    // ── Deadline ──────────────────────────────────────────────
    public static DeadlineResponse toDto(Deadline d) {
        return DeadlineResponse.builder()
                .id(d.getId())
                .title(d.getTitle())
                .description(d.getDescription())
                .dueDate(d.getDueDate())
                .repeatRule(d.getRepeatRule() != null ? d.getRepeatRule().name() : null)
                .isCustom(d.getIsCustom())
                .build();
    }

    // ── Procurement Scenario ──────────────────────────────────
    public static ScenarioResponse toDto(ProcurementScenario s) {
        return ScenarioResponse.builder()
                .id(s.getId())
                .lawType(s.getLawType().name())
                .lawTypeDisplay(s.getLawType().getDisplayName())
                .title(s.getTitle())
                .description(s.getDescription())
                .mspOnly(s.getMspOnly())
                .amountMin(s.getAmountMin())
                .amountMax(s.getAmountMax())
                .build();
    }

    public static RiskCardResponse toDto(RiskCard r) {
        return RiskCardResponse.builder()
                .id(r.getId())
                .title(r.getTitle())
                .riskType(r.getRiskType() != null ? r.getRiskType().name() : null)
                .riskTypeDisplay(r.getRiskType() != null ? r.getRiskType().getDisplayName() : null)
                .description(r.getDescription())
                .consequence(r.getConsequence())
                .recommendation(r.getRecommendation())
                .build();
    }

    // ── Checklist ─────────────────────────────────────────────
    public static ChecklistResponse toDto(Checklist cl) {
        return ChecklistResponse.builder()
                .id(cl.getId())
                .title(cl.getTitle())
                .description(cl.getDescription())
                .isTemplate(cl.getIsTemplate())
                .isCompleted(cl.getIsCompleted())
                .progressPercent(cl.getProgressPercent())
                .totalSteps(cl.getSteps().size())
                .completedSteps(cl.getCompletedStepsCount())
                .steps(cl.getSteps().stream().map(DtoMapper::toDto).collect(Collectors.toList()))
                .build();
    }

    public static ChecklistStepResponse toDto(ChecklistStep s) {
        return ChecklistStepResponse.builder()
                .id(s.getId())
                .stepOrder(s.getStepOrder())
                .title(s.getTitle())
                .description(s.getDescription())
                .hint(s.getHint())
                .isCompleted(s.getIsCompleted())
                .completedAt(s.getCompletedAt())
                .build();
    }
}
