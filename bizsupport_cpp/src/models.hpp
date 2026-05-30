#pragma once

#include <string>
#include <vector>
#include <optional>
#include <ctime>
#include <chrono>
#include <stdexcept>

// ============================================================================
// Enums
// ============================================================================

enum class Role {
    USER,
    ADMIN
};

inline std::string role_to_string(Role r) {
    switch (r) {
        case Role::USER:  return "USER";
        case Role::ADMIN: return "ADMIN";
    }
    return "USER";
}

inline Role role_from_string(const std::string& s) {
    if (s == "ADMIN") return Role::ADMIN;
    if (s == "USER")  return Role::USER;
    throw std::invalid_argument("Unknown Role: " + s);
}

// ---------------------------------------------------------------------------

enum class CompanyType {
    IP,
    OOO
};

inline std::string company_type_to_string(CompanyType t) {
    switch (t) {
        case CompanyType::IP:  return "IP";
        case CompanyType::OOO: return "OOO";
    }
    return "IP";
}

inline CompanyType company_type_from_string(const std::string& s) {
    if (s == "IP")  return CompanyType::IP;
    if (s == "OOO") return CompanyType::OOO;
    throw std::invalid_argument("Unknown CompanyType: " + s);
}

inline std::string company_type_display(CompanyType t) {
    switch (t) {
        case CompanyType::IP:  return "\xD0\x98\xD0\x9F";          // "ИП" in UTF-8
        case CompanyType::OOO: return "\xD0\x9E\xD0\x9E\xD0\x9E"; // "ООО" in UTF-8
    }
    return "";
}

// ---------------------------------------------------------------------------

enum class EntityType {
    TAX_REGIME,
    PROCUREMENT,
    RISK,
    TENDER
};

inline std::string entity_type_to_string(EntityType t) {
    switch (t) {
        case EntityType::TAX_REGIME:   return "TAX_REGIME";
        case EntityType::PROCUREMENT:  return "PROCUREMENT";
        case EntityType::RISK:         return "RISK";
        case EntityType::TENDER:       return "TENDER";
    }
    return "TAX_REGIME";
}

inline EntityType entity_type_from_string(const std::string& s) {
    if (s == "TAX_REGIME")  return EntityType::TAX_REGIME;
    if (s == "PROCUREMENT") return EntityType::PROCUREMENT;
    if (s == "RISK")        return EntityType::RISK;
    if (s == "TENDER")      return EntityType::TENDER;
    throw std::invalid_argument("Unknown EntityType: " + s);
}

inline std::string entity_type_display(EntityType t) {
    switch (t) {
        case EntityType::TAX_REGIME:
            return "\xD0\x9D\xD0\xB0\xD0\xBB\xD0\xBE\xD0\xB3\xD0\xBE\xD0\xB2\xD1\x8B\xD0\xB9 "
                   "\xD1\x80\xD0\xB5\xD0\xB6\xD0\xB8\xD0\xBC"; // "Налоговый режим"
        case EntityType::PROCUREMENT:
            return "\xD0\xA1\xD1\x86\xD0\xB5\xD0\xBD\xD0\xB0\xD1\x80\xD0\xB8\xD0\xB9 "
                   "\xD0\xB7\xD0\xB0\xD0\xBA\xD1\x83\xD0\xBF\xD0\xBA\xD0\xB8"; // "Сценарий закупки"
        case EntityType::RISK:
            return "\xD0\x9A\xD0\xB0\xD1\x80\xD1\x82\xD0\xBE\xD1\x87\xD0\xBA\xD0\xB0 "
                   "\xD1\x80\xD0\xB8\xD1\x81\xD0\xBA\xD0\xB0"; // "Карточка риска"
        case EntityType::TENDER:
            return "\xD0\xA2\xD0\xB5\xD0\xBD\xD0\xB4\xD0\xB5\xD1\x80"; // "Тендер"
    }
    return "";
}

// ---------------------------------------------------------------------------

enum class RepeatRule {
    ANNUAL,
    QUARTERLY,
    MONTHLY,
    NONE
};

inline std::string repeat_rule_to_string(RepeatRule r) {
    switch (r) {
        case RepeatRule::ANNUAL:    return "ANNUAL";
        case RepeatRule::QUARTERLY: return "QUARTERLY";
        case RepeatRule::MONTHLY:   return "MONTHLY";
        case RepeatRule::NONE:      return "NONE";
    }
    return "NONE";
}

inline RepeatRule repeat_rule_from_string(const std::string& s) {
    if (s == "ANNUAL")    return RepeatRule::ANNUAL;
    if (s == "QUARTERLY") return RepeatRule::QUARTERLY;
    if (s == "MONTHLY")   return RepeatRule::MONTHLY;
    if (s == "NONE")      return RepeatRule::NONE;
    throw std::invalid_argument("Unknown RepeatRule: " + s);
}

// ---------------------------------------------------------------------------

enum class TenderLawType {
    FZ_44,
    FZ_223,
    COMMERCIAL
};

inline std::string tender_law_type_to_string(TenderLawType t) {
    switch (t) {
        case TenderLawType::FZ_44:       return "FZ_44";
        case TenderLawType::FZ_223:      return "FZ_223";
        case TenderLawType::COMMERCIAL:  return "COMMERCIAL";
    }
    return "FZ_44";
}

inline TenderLawType tender_law_type_from_string(const std::string& s) {
    if (s == "FZ_44")      return TenderLawType::FZ_44;
    if (s == "FZ_223")     return TenderLawType::FZ_223;
    if (s == "COMMERCIAL") return TenderLawType::COMMERCIAL;
    throw std::invalid_argument("Unknown TenderLawType: " + s);
}

inline std::string tender_law_type_display(TenderLawType t) {
    switch (t) {
        case TenderLawType::FZ_44:      return "44-\xD0\xA4\xD0\x97";                               // "44-ФЗ"
        case TenderLawType::FZ_223:     return "223-\xD0\xA4\xD0\x97";                              // "223-ФЗ"
        case TenderLawType::COMMERCIAL: return "\xD0\x9A\xD0\xBE\xD0\xBC\xD0\xBC\xD0\xB5\xD1\x80"
                                               "\xD1\x87\xD0\xB5\xD1\x81\xD0\xBA\xD0\xB0\xD1\x8F"; // "Коммерческая"
    }
    return "";
}

// ---------------------------------------------------------------------------

enum class TenderStatus {
    PUBLISHED,
    UNDER_REVIEW,
    AUCTION,
    COMPLETED,
    CANCELLED
};

inline std::string tender_status_to_string(TenderStatus s) {
    switch (s) {
        case TenderStatus::PUBLISHED:    return "PUBLISHED";
        case TenderStatus::UNDER_REVIEW: return "UNDER_REVIEW";
        case TenderStatus::AUCTION:      return "AUCTION";
        case TenderStatus::COMPLETED:    return "COMPLETED";
        case TenderStatus::CANCELLED:    return "CANCELLED";
    }
    return "PUBLISHED";
}

inline TenderStatus tender_status_from_string(const std::string& s) {
    if (s == "PUBLISHED")    return TenderStatus::PUBLISHED;
    if (s == "UNDER_REVIEW") return TenderStatus::UNDER_REVIEW;
    if (s == "AUCTION")      return TenderStatus::AUCTION;
    if (s == "COMPLETED")    return TenderStatus::COMPLETED;
    if (s == "CANCELLED")    return TenderStatus::CANCELLED;
    throw std::invalid_argument("Unknown TenderStatus: " + s);
}

inline std::string tender_status_display_name(TenderStatus s) {
    switch (s) {
        case TenderStatus::PUBLISHED:
            return "\xD0\x9F\xD0\xBE\xD0\xB4\xD0\xB0\xD1\x87\xD0\xB0 "
                   "\xD0\xB7\xD0\xB0\xD1\x8F\xD0\xB2\xD0\xBE\xD0\xBA"; // "Подача заявок"
        case TenderStatus::UNDER_REVIEW:
            return "\xD0\xA0\xD0\xB0\xD1\x81\xD1\x81\xD0\xBC\xD0\xBE\xD1\x82\xD1\x80\xD0\xB5"
                   "\xD0\xBD\xD0\xB8\xD0\xB5 \xD0\xB7\xD0\xB0\xD1\x8F\xD0\xB2\xD0\xBE\xD0\xBA"; // "Рассмотрение заявок"
        case TenderStatus::AUCTION:
            return "\xD0\xA2\xD0\xBE\xD1\x80\xD0\xB3\xD0\xB8"; // "Торги"
        case TenderStatus::COMPLETED:
            return "\xD0\x97\xD0\xB0\xD0\xB2\xD0\xB5\xD1\x80\xD1\x88\xD0\xB5\xD0\xBD\xD0\xB0"; // "Завершена"
        case TenderStatus::CANCELLED:
            return "\xD0\x9E\xD1\x82\xD0\xBC\xD0\xB5\xD0\xBD\xD0\xB5\xD0\xBD\xD0\xB0"; // "Отменена"
    }
    return "";
}

inline std::string tender_status_badge_style(TenderStatus s) {
    switch (s) {
        case TenderStatus::PUBLISHED:    return "success";
        case TenderStatus::UNDER_REVIEW: return "warning";
        case TenderStatus::AUCTION:      return "info";
        case TenderStatus::COMPLETED:    return "muted";
        case TenderStatus::CANCELLED:    return "danger";
    }
    return "";
}

// ---------------------------------------------------------------------------

enum class RiskType {
    FINANCIAL,
    LEGAL,
    PROCEDURAL
};

inline std::string risk_type_to_string(RiskType r) {
    switch (r) {
        case RiskType::FINANCIAL:  return "FINANCIAL";
        case RiskType::LEGAL:      return "LEGAL";
        case RiskType::PROCEDURAL: return "PROCEDURAL";
    }
    return "FINANCIAL";
}

inline RiskType risk_type_from_string(const std::string& s) {
    if (s == "FINANCIAL")  return RiskType::FINANCIAL;
    if (s == "LEGAL")      return RiskType::LEGAL;
    if (s == "PROCEDURAL") return RiskType::PROCEDURAL;
    throw std::invalid_argument("Unknown RiskType: " + s);
}

inline std::string risk_type_display(RiskType r) {
    switch (r) {
        case RiskType::FINANCIAL:
            return "\xD0\xA4\xD0\xB8\xD0\xBD\xD0\xB0\xD0\xBD\xD1\x81\xD0\xBE\xD0\xB2\xD1\x8B\xD0\xB9"; // "Финансовый"
        case RiskType::LEGAL:
            return "\xD0\xAE\xD1\x80\xD0\xB8\xD0\xB4\xD0\xB8\xD1\x87\xD0\xB5\xD1\x81\xD0\xBA\xD0\xB8\xD0\xB9"; // "Юридический"
        case RiskType::PROCEDURAL:
            return "\xD0\x9F\xD1\x80\xD0\xBE\xD1\x86\xD0\xB5\xD0\xB4\xD1\x83\xD1\x80\xD0\xBD\xD1\x8B\xD0\xB9"; // "Процедурный"
    }
    return "";
}

// ---------------------------------------------------------------------------

enum class LawType {
    FZ_44,
    FZ_223
};

inline std::string law_type_to_string(LawType t) {
    switch (t) {
        case LawType::FZ_44:  return "FZ_44";
        case LawType::FZ_223: return "FZ_223";
    }
    return "FZ_44";
}

inline LawType law_type_from_string(const std::string& s) {
    if (s == "FZ_44")  return LawType::FZ_44;
    if (s == "FZ_223") return LawType::FZ_223;
    throw std::invalid_argument("Unknown LawType: " + s);
}

inline std::string law_type_display(LawType t) {
    switch (t) {
        case LawType::FZ_44:  return "44-\xD0\xA4\xD0\x97";  // "44-ФЗ"
        case LawType::FZ_223: return "223-\xD0\xA4\xD0\x97"; // "223-ФЗ"
    }
    return "";
}

// ============================================================================
// Model structs
// ============================================================================

struct User {
    int64_t     id = 0;
    std::string email;
    std::string password;
    std::string full_name;
    Role        role = Role::USER;
    std::string created_at;
    std::string updated_at;
};

struct CompanyProfile {
    int64_t              id = 0;
    int64_t              user_id = 0;
    std::string          company_name;
    std::string          company_type_str;   // "IP" or "OOO"
    std::string          inn;
    std::string          ogrn;
    std::string          industry;
    std::optional<int>   employees_count;
    std::optional<double> annual_revenue;
    bool                 is_msp = false;
    std::string          created_at;
    std::string          updated_at;
};

struct TaxRegime {
    int64_t     id = 0;
    std::string code;          // USN_6, USN_15, OSNO, PSN, NPD
    std::string name;
    std::string description;
    std::string conditions;    // JSON string with conditions
    std::string nk_ref;        // reference to article of NK RF
};

struct CompanyTaxRegime {
    int64_t     id = 0;
    int64_t     company_id = 0;
    int64_t     tax_regime_id = 0;
    bool        is_current = true;
    std::string applied_since;

    // Denormalized fields loaded via JOIN (for JSON output)
    std::string regime_code;
    std::string regime_name;
};

struct TaxObligation {
    int64_t     id = 0;
    int64_t     tax_regime_id = 0;
    std::string tax_name;
    std::string rate;
    std::string description;
    std::string nk_ref;
    std::string fns_service_url;
};

struct Tender {
    int64_t              id = 0;
    std::string          registry_number;
    std::string          title;
    std::string          description;
    std::string          customer_name;
    std::string          customer_inn;
    std::string          law_type;             // "FZ_44", "FZ_223", "COMMERCIAL"
    std::string          procurement_method;
    std::optional<double> initial_price;
    std::string          currency;
    std::string          region;
    std::string          okpd_code;
    std::string          category;
    std::string          published_at;
    std::string          submission_deadline;
    std::string          auction_date;
    std::optional<double> application_security;
    std::optional<double> contract_security;
    bool                 msp_only = false;
    std::string          status;               // "PUBLISHED", "UNDER_REVIEW", etc.
    std::string          source_url;
    std::string          source;
    std::string          created_at;
};

struct Deadline {
    int64_t                id = 0;
    std::optional<int64_t> tax_regime_id;
    std::optional<int64_t> company_id;
    std::string            title;
    std::string            description;
    std::string            due_date;
    std::string            repeat_rule;        // "ANNUAL", "QUARTERLY", "MONTHLY", "NONE"
    bool                   is_custom = false;
    std::string            created_at;
};

struct Notification {
    int64_t                id = 0;
    int64_t                user_id = 0;
    std::optional<int64_t> deadline_id;
    std::string            title;
    std::string            message;
    bool                   is_read = false;
    std::string            send_at;
    std::string            created_at;
};

struct UserFavorite {
    int64_t     id = 0;
    int64_t     user_id = 0;
    std::string entity_type;       // "TAX_REGIME", "PROCUREMENT", "RISK", "TENDER"
    int64_t     entity_id = 0;
    std::string created_at;
};

struct ProcurementScenario {
    int64_t              id = 0;
    std::string          law_type;             // "FZ_44" or "FZ_223"
    std::string          title;
    std::string          description;
    bool                 msp_only = false;
    std::optional<double> amount_min;
    std::optional<double> amount_max;
};

struct RiskCard {
    int64_t     id = 0;
    int64_t     scenario_id = 0;
    std::string title;
    std::string risk_type;         // "FINANCIAL", "LEGAL", "PROCEDURAL"
    std::string description;
    std::string consequence;
    std::string recommendation;
};

struct ChecklistStep {
    int64_t                id = 0;
    int64_t                checklist_id = 0;
    int                    step_order = 0;
    std::string            title;
    std::string            description;
    std::string            hint;
    bool                   is_completed = false;
    std::optional<std::string> completed_at;
};

struct Checklist {
    int64_t                    id = 0;
    std::optional<int64_t>     user_id;
    std::optional<int64_t>     scenario_id;
    std::string                title;
    std::string                description;
    bool                       is_template = false;
    bool                       is_completed = false;
    std::string                created_at;
    std::string                updated_at;
    std::vector<ChecklistStep> steps;

    int completed_steps_count() const {
        int count = 0;
        for (const auto& s : steps) {
            if (s.is_completed) ++count;
        }
        return count;
    }

    int progress_percent() const {
        if (steps.empty()) return 0;
        return static_cast<int>((completed_steps_count() * 100.0) / steps.size());
    }
};

struct LegalReference {
    int64_t     id = 0;
    std::string entity_type;       // "TAX_REGIME", "PROCUREMENT", "RISK", "TENDER"
    int64_t     entity_id = 0;
    std::string title;
    std::string url;
    std::string article;
};
