#pragma once

#include "models.hpp"
#include <nlohmann/json.hpp>

// ============================================================================
// Helper: convert std::optional to nlohmann::json (null if empty)
// ============================================================================

template <typename T>
inline nlohmann::json opt_to_json(const std::optional<T>& opt) {
    if (opt.has_value()) return opt.value();
    return nullptr;
}

// ============================================================================
// Model -> JSON serialization functions
// ============================================================================

inline nlohmann::json user_to_json(const User& u) {
    return {
        {"id",        u.id},
        {"email",     u.email},
        {"fullName",  u.full_name},
        {"role",      role_to_string(u.role)},
        {"createdAt", u.created_at}
    };
}

inline nlohmann::json company_profile_to_json(const CompanyProfile& p) {
    // Derive display name from company_type_str
    std::string type_display;
    if (p.company_type_str == "IP") {
        type_display = company_type_display(CompanyType::IP);
    } else if (p.company_type_str == "OOO") {
        type_display = company_type_display(CompanyType::OOO);
    } else {
        type_display = p.company_type_str;
    }

    return {
        {"id",                 p.id},
        {"companyName",        p.company_name},
        {"companyType",        p.company_type_str},
        {"companyTypeDisplay", type_display},
        {"inn",                p.inn},
        {"ogrn",               p.ogrn},
        {"industry",           p.industry},
        {"employeesCount",     opt_to_json(p.employees_count)},
        {"annualRevenue",      opt_to_json(p.annual_revenue)},
        {"isMsp",              p.is_msp},
        {"createdAt",          p.created_at},
        {"updatedAt",          p.updated_at}
    };
}

inline nlohmann::json tax_regime_to_json(const TaxRegime& r) {
    return {
        {"id",          r.id},
        {"code",        r.code},
        {"name",        r.name},
        {"description", r.description},
        {"conditions",  r.conditions},
        {"nkRef",       r.nk_ref}
    };
}

inline nlohmann::json tax_obligation_to_json(const TaxObligation& o) {
    return {
        {"id",            o.id},
        {"taxName",       o.tax_name},
        {"rate",          o.rate},
        {"description",   o.description},
        {"nkRef",         o.nk_ref},
        {"fnsServiceUrl", o.fns_service_url}
    };
}

inline nlohmann::json company_tax_regime_to_json(const CompanyTaxRegime& ctr) {
    return {
        {"id",           ctr.id},
        {"regimeCode",   ctr.regime_code},
        {"regimeName",   ctr.regime_name},
        {"isCurrent",    ctr.is_current},
        {"appliedSince", ctr.applied_since}
    };
}

inline nlohmann::json deadline_to_json(const Deadline& d) {
    return {
        {"id",          d.id},
        {"title",       d.title},
        {"description", d.description},
        {"dueDate",     d.due_date},
        {"repeatRule",  d.repeat_rule},
        {"isCustom",    d.is_custom}
    };
}

inline nlohmann::json notification_to_json(const Notification& n) {
    return {
        {"id",         n.id},
        {"userId",     n.user_id},
        {"deadlineId", opt_to_json(n.deadline_id)},
        {"title",      n.title},
        {"message",    n.message},
        {"isRead",     n.is_read},
        {"sendAt",     n.send_at},
        {"createdAt",  n.created_at}
    };
}

inline nlohmann::json favorite_to_json(const UserFavorite& f) {
    return {
        {"id",         f.id},
        {"userId",     f.user_id},
        {"entityType", f.entity_type},
        {"entityId",   f.entity_id},
        {"createdAt",  f.created_at}
    };
}

inline nlohmann::json scenario_to_json(const ProcurementScenario& s) {
    // Derive display name from law_type string
    std::string law_display;
    if (s.law_type == "FZ_44") {
        law_display = law_type_display(LawType::FZ_44);
    } else if (s.law_type == "FZ_223") {
        law_display = law_type_display(LawType::FZ_223);
    } else {
        law_display = s.law_type;
    }

    return {
        {"id",             s.id},
        {"lawType",        s.law_type},
        {"lawTypeDisplay", law_display},
        {"title",          s.title},
        {"description",    s.description},
        {"mspOnly",        s.msp_only},
        {"amountMin",      opt_to_json(s.amount_min)},
        {"amountMax",      opt_to_json(s.amount_max)}
    };
}

inline nlohmann::json risk_card_to_json(const RiskCard& r) {
    // Derive display name from risk_type string
    std::string risk_display;
    if (!r.risk_type.empty()) {
        try {
            risk_display = risk_type_display(risk_type_from_string(r.risk_type));
        } catch (...) {
            risk_display = r.risk_type;
        }
    }

    return {
        {"id",              r.id},
        {"title",           r.title},
        {"riskType",        r.risk_type.empty() ? nlohmann::json(nullptr) : nlohmann::json(r.risk_type)},
        {"riskTypeDisplay", r.risk_type.empty() ? nlohmann::json(nullptr) : nlohmann::json(risk_display)},
        {"description",     r.description},
        {"consequence",     r.consequence},
        {"recommendation",  r.recommendation}
    };
}

inline nlohmann::json checklist_step_to_json(const ChecklistStep& s) {
    return {
        {"id",          s.id},
        {"stepOrder",   s.step_order},
        {"title",       s.title},
        {"description", s.description},
        {"hint",        s.hint},
        {"isCompleted", s.is_completed},
        {"completedAt", opt_to_json(s.completed_at)}
    };
}

inline nlohmann::json checklist_to_json(const Checklist& cl) {
    nlohmann::json steps_json = nlohmann::json::array();
    for (const auto& step : cl.steps) {
        steps_json.push_back(checklist_step_to_json(step));
    }

    return {
        {"id",              cl.id},
        {"title",           cl.title},
        {"description",     cl.description},
        {"isTemplate",      cl.is_template},
        {"isCompleted",     cl.is_completed},
        {"progressPercent", cl.progress_percent()},
        {"totalSteps",      static_cast<int>(cl.steps.size())},
        {"completedSteps",  cl.completed_steps_count()},
        {"steps",           steps_json}
    };
}

inline nlohmann::json legal_reference_to_json(const LegalReference& lr) {
    return {
        {"id",         lr.id},
        {"entityType", lr.entity_type},
        {"entityId",   lr.entity_id},
        {"title",      lr.title},
        {"url",        lr.url},
        {"article",    lr.article}
    };
}

// ── Tender card (compact, for list views) ────────────────────────────────────

inline nlohmann::json tender_card_to_json(const Tender& t) {
    // Derive display values from status and law_type strings
    std::string status_display;
    std::string status_badge;
    if (!t.status.empty()) {
        try {
            TenderStatus ts = tender_status_from_string(t.status);
            status_display  = tender_status_display_name(ts);
            status_badge    = tender_status_badge_style(ts);
        } catch (...) {
            status_display = t.status;
            status_badge   = "";
        }
    }

    std::string law_display;
    if (!t.law_type.empty()) {
        try {
            TenderLawType lt = tender_law_type_from_string(t.law_type);
            law_display = tender_law_type_display(lt);
        } catch (...) {
            law_display = t.law_type;
        }
    }

    return {
        {"id",                 t.id},
        {"registryNumber",     t.registry_number},
        {"title",              t.title},
        {"customerName",       t.customer_name},
        {"lawType",            t.law_type},
        {"lawTypeDisplay",     law_display},
        {"initialPrice",       opt_to_json(t.initial_price)},
        {"region",             t.region},
        {"category",           t.category},
        {"submissionDeadline", t.submission_deadline},
        {"status",             t.status},
        {"statusDisplay",      status_display},
        {"statusBadge",        status_badge},
        {"mspOnly",            t.msp_only}
    };
}

// ── Tender detail (all fields) ───────────────────────────────────────────────

inline nlohmann::json tender_detail_to_json(const Tender& t) {
    std::string status_display;
    std::string status_badge;
    if (!t.status.empty()) {
        try {
            TenderStatus ts = tender_status_from_string(t.status);
            status_display  = tender_status_display_name(ts);
            status_badge    = tender_status_badge_style(ts);
        } catch (...) {
            status_display = t.status;
            status_badge   = "";
        }
    }

    std::string law_display;
    if (!t.law_type.empty()) {
        try {
            TenderLawType lt = tender_law_type_from_string(t.law_type);
            law_display = tender_law_type_display(lt);
        } catch (...) {
            law_display = t.law_type;
        }
    }

    return {
        {"id",                  t.id},
        {"registryNumber",      t.registry_number},
        {"title",               t.title},
        {"description",         t.description},
        {"customerName",        t.customer_name},
        {"customerInn",         t.customer_inn},
        {"lawType",             t.law_type},
        {"lawTypeDisplay",      law_display},
        {"procurementMethod",   t.procurement_method},
        {"initialPrice",        opt_to_json(t.initial_price)},
        {"currency",            t.currency},
        {"region",              t.region},
        {"okpdCode",            t.okpd_code},
        {"category",            t.category},
        {"publishedAt",         t.published_at},
        {"submissionDeadline",  t.submission_deadline},
        {"auctionDate",         t.auction_date},
        {"applicationSecurity", opt_to_json(t.application_security)},
        {"contractSecurity",    opt_to_json(t.contract_security)},
        {"mspOnly",             t.msp_only},
        {"status",              t.status},
        {"statusDisplay",       status_display},
        {"statusBadge",         status_badge},
        {"sourceUrl",           t.source_url},
        {"source",              t.source},
        {"createdAt",           t.created_at}
    };
}

// ── Generic response helpers ─────────────────────────────────────────────────

inline nlohmann::json error_response(int status, const std::string& error,
                                     const std::string& message) {
    return {
        {"status",  status},
        {"error",   error},
        {"message", message}
    };
}

inline nlohmann::json message_response(const std::string& message) {
    return {
        {"message", message}
    };
}
