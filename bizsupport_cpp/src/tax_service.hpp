#pragma once
#include "repositories.hpp"
#include <nlohmann/json.hpp>
#include <optional>
#include <string>
#include <vector>

class TaxService {
public:
    TaxService(Repositories& repos) : repos_(repos) {}

    // Get all available tax regimes
    std::vector<TaxRegime> get_all_regimes() {
        return repos_.find_all_tax_regimes();
    }

    // Find a specific regime by its code (e.g. "USN_6", "OSNO")
    std::optional<TaxRegime> find_by_code(const std::string& code) {
        return repos_.find_tax_regime_by_code(code);
    }

    // Get tax obligations for a given regime
    std::vector<TaxObligation> get_obligations(int64_t tax_regime_id) {
        return repos_.find_obligations_by_regime_id(tax_regime_id);
    }

    // Get template deadlines for a regime (not associated with a specific company)
    std::vector<Deadline> get_template_deadlines(int64_t tax_regime_id) {
        return repos_.find_template_deadlines_by_regime_id(tax_regime_id);
    }

    // Get deadlines associated with a specific company
    std::vector<Deadline> get_company_deadlines(int64_t company_id) {
        return repos_.find_deadlines_by_company_id(company_id);
    }

    // Recommend tax regimes based on company parameters
    std::vector<TaxRegime> recommend(std::optional<std::string> company_type,
                                     std::optional<int> employees,
                                     std::optional<double> revenue) {
        auto all = repos_.find_all_tax_regimes();
        std::vector<TaxRegime> result;

        for (const auto& regime : all) {
            if (matches(regime, company_type, employees, revenue)) {
                result.push_back(regime);
            }
        }
        return result;
    }

private:
    Repositories& repos_;

    // Check if a company matches the conditions for a given regime
    bool matches(const TaxRegime& regime,
                 const std::optional<std::string>& company_type,
                 const std::optional<int>& employees,
                 const std::optional<double>& revenue) {

        // OSNO is available to everyone
        if (regime.code == "OSNO") return true;

        // NPD: only IP, no employees, revenue <= 2.4M
        if (regime.code == "NPD") {
            if (company_type && *company_type != "IP") return false;
            if (employees && *employees > 0) return false;
            if (revenue && *revenue > 2400000.0) return false;
            return (!company_type || *company_type == "IP");
        }

        // PSN: only IP, employees <= 15, revenue <= 60M
        if (regime.code == "PSN") {
            if (company_type && *company_type != "IP") return false;
            if (employees && *employees > 15) return false;
            if (revenue && *revenue > 60000000.0) return false;
            return (!company_type || *company_type == "IP");
        }

        // USN (USN_6, USN_15): IP or OOO, employees <= 130, revenue <= 265.8M
        if (regime.code.substr(0, 3) == "USN") {
            if (employees && *employees > 130) return false;
            if (revenue && *revenue > 265800000.0) return false;
            return true;
        }

        // If regime has conditions as JSON, try to parse and check
        if (!regime.conditions.empty()) {
            try {
                auto cond = nlohmann::json::parse(regime.conditions);

                if (cond.contains("max_revenue") && revenue) {
                    if (*revenue > cond["max_revenue"].get<double>()) return false;
                }
                if (cond.contains("max_employees") && employees) {
                    if (*employees > cond["max_employees"].get<int>()) return false;
                }
                if (cond.contains("allowed_types") && company_type) {
                    auto& types = cond["allowed_types"];
                    bool found = false;
                    for (const auto& t : types) {
                        if (t.get<std::string>() == *company_type) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) return false;
                }
                return true;
            } catch (...) {
                // If conditions parsing fails, exclude this regime
                return false;
            }
        }

        return false;
    }
};
