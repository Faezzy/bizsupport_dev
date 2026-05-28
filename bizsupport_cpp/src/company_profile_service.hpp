#pragma once
#include "repositories.hpp"
#include <nlohmann/json.hpp>
#include <optional>
#include <string>
#include <vector>

class CompanyProfileService {
public:
    CompanyProfileService(Repositories& repos) : repos_(repos) {}

    // Find company profile by user ID
    std::optional<CompanyProfile> find_by_user_id(int64_t user_id) {
        return repos_.find_profile_by_user_id(user_id);
    }

    // Create or update company profile
    CompanyProfile save_or_update(int64_t user_id,
                                  const std::string& company_name,
                                  const std::string& company_type,
                                  const std::string& inn,
                                  const std::string& industry,
                                  std::optional<int> employees,
                                  std::optional<double> revenue) {
        return repos_.save_or_update_profile(user_id, company_name, company_type, inn, industry, employees, revenue);
    }

    // Set tax regime for a company. Finds regime by code, clears current regimes, sets new one.
    void set_tax_regime(int64_t company_id, const std::string& regime_code) {
        auto regime_opt = repos_.find_tax_regime_by_code(regime_code);
        if (!regime_opt) {
            throw std::runtime_error("Tax regime not found: " + regime_code);
        }
        repos_.clear_current_regimes(company_id);
        repos_.set_tax_regime(company_id, regime_opt->id);
    }

    // Get current tax regimes for a company
    std::vector<CompanyTaxRegime> get_current_regimes(int64_t company_id) {
        return repos_.find_current_regimes(company_id);
    }

private:
    Repositories& repos_;
};
