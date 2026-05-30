#pragma once
#include "repositories.hpp"
#include "mock_tender_provider.hpp"
#include "json_utils.hpp"
#include <nlohmann/json.hpp>
#include <optional>
#include <string>
#include <vector>
#include <algorithm>

class TenderService {
public:
    TenderService(Repositories& repos) : repos_(repos) {}

    // Search tenders with filters and pagination
    // Returns JSON: {items, totalItems, totalPages, currentPage, availableRegions, availableCategories}
    nlohmann::json search(const std::string& query = "",
                          const std::string& law_type = "",
                          const std::string& status = "",
                          const std::string& region = "",
                          const std::string& category = "",
                          std::optional<double> price_from = std::nullopt,
                          std::optional<double> price_to = std::nullopt,
                          bool msp_only = false,
                          int page = 0,
                          int size = 20,
                          const std::string& sort = "") {

        auto tenders = repos_.search_tenders(query, law_type, status, region, category,
                                              price_from, price_to, msp_only, page, size, sort);
        int64_t total_items = repos_.count_search_results(query, law_type, status, region, category,
                                                           price_from, price_to, msp_only);

        int64_t total_pages = (total_items + size - 1) / size;

        // Build items array
        nlohmann::json items = nlohmann::json::array();
        for (const auto& t : tenders) {
            items.push_back(tender_card_to_json(t));
        }

        // Get available regions and categories for filter UI
        auto regions = repos_.find_distinct_regions();
        auto categories = repos_.find_distinct_categories();

        std::sort(regions.begin(), regions.end());
        std::sort(categories.begin(), categories.end());

        nlohmann::json result;
        result["items"] = items;
        result["totalItems"] = total_items;
        result["totalPages"] = total_pages;
        result["currentPage"] = page;
        result["availableRegions"] = regions;
        result["availableCategories"] = categories;
        return result;
    }

    // Get full tender detail by ID
    std::optional<nlohmann::json> get_detail(int64_t id) {
        auto tender_opt = repos_.find_tender_by_id(id);
        if (!tender_opt) return std::nullopt;
        return tender_detail_to_json(*tender_opt);
    }

    // Get aggregated statistics
    nlohmann::json get_stats() {
        int64_t total = repos_.count_tenders();
        int64_t published = repos_.count_tenders_by_status("PUBLISHED");
        int64_t under_review = repos_.count_tenders_by_status("UNDER_REVIEW");
        int64_t completed = repos_.count_tenders_by_status("COMPLETED");

        return {
            {"total", total},
            {"published", published},
            {"underReview", under_review},
            {"completed", completed}
        };
    }

    // Load initial mock tenders into DB if the database is empty
    void load_initial_tenders() {
        if (repos_.count_tenders() > 0) return;

        MockTenderProvider provider;
        auto tenders = provider.generate();
        for (auto& t : tenders) {
            repos_.save_tender(t);
        }
    }

private:
    Repositories& repos_;
};
