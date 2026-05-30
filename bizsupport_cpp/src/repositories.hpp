#pragma once
#include "models.hpp"
#include "database.hpp"
#include <pqxx/pqxx>
#include <vector>
#include <optional>
#include <string>
#include <stdexcept>

class Repositories {
public:
    explicit Repositories(ConnectionPool& pool) : pool_(pool) {}

    // ========================================================================
    // User operations
    // ========================================================================

    std::optional<User> find_user_by_email(const std::string& email) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, email, password, full_name, role, created_at, updated_at "
            "FROM users WHERE email = $1",
            email);
        txn.commit();
        if (result.empty()) return std::nullopt;
        return parse_user(result[0]);
    }

    std::optional<User> find_user_by_id(int64_t id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, email, password, full_name, role, created_at, updated_at "
            "FROM users WHERE id = $1",
            id);
        txn.commit();
        if (result.empty()) return std::nullopt;
        return parse_user(result[0]);
    }

    bool exists_by_email(const std::string& email) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT COUNT(*) FROM users WHERE email = $1",
            email);
        txn.commit();
        return result[0][0].as<int64_t>() > 0;
    }

    User save_user(const std::string& email, const std::string& password_hash,
                   const std::string& full_name, const std::string& role = "USER") {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "INSERT INTO users (email, password, full_name, role, created_at, updated_at) "
            "VALUES ($1, $2, $3, $4, NOW(), NOW()) "
            "RETURNING id, email, password, full_name, role, created_at, updated_at",
            email, password_hash, full_name, role);
        txn.commit();
        return parse_user(result[0]);
    }

    std::vector<User> find_all_users() {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec(
            "SELECT id, email, password, full_name, role, created_at, updated_at "
            "FROM users ORDER BY id");
        txn.commit();
        std::vector<User> users;
        users.reserve(result.size());
        for (const auto& row : result) {
            users.push_back(parse_user(row));
        }
        return users;
    }

    void delete_user(int64_t id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        txn.exec_params("DELETE FROM users WHERE id = $1", id);
        txn.commit();
    }

    void update_user_role(int64_t id, const std::string& role) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        txn.exec_params(
            "UPDATE users SET role = $1, updated_at = NOW() WHERE id = $2",
            role, id);
        txn.commit();
    }

    // ========================================================================
    // CompanyProfile operations
    // ========================================================================

    std::optional<CompanyProfile> find_profile_by_user_id(int64_t user_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, user_id, company_name, company_type, inn, ogrn, industry, "
            "employees_count, annual_revenue, is_msp, created_at, updated_at "
            "FROM company_profiles WHERE user_id = $1",
            user_id);
        txn.commit();
        if (result.empty()) return std::nullopt;
        return parse_company_profile(result[0]);
    }

    bool profile_exists_by_user_id(int64_t user_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT COUNT(*) FROM company_profiles WHERE user_id = $1",
            user_id);
        txn.commit();
        return result[0][0].as<int64_t>() > 0;
    }

    CompanyProfile save_or_update_profile(int64_t user_id,
                                          const std::string& company_name,
                                          const std::string& company_type,
                                          const std::string& inn,
                                          const std::string& industry,
                                          std::optional<int> employees_count,
                                          std::optional<double> annual_revenue) {
        bool is_msp = false;
        if (employees_count.has_value() && annual_revenue.has_value()) {
            is_msp = (employees_count.value() <= 250) && (annual_revenue.value() <= 2000000000.0);
        } else if (employees_count.has_value()) {
            is_msp = (employees_count.value() <= 250);
        } else if (annual_revenue.has_value()) {
            is_msp = (annual_revenue.value() <= 2000000000.0);
        }

        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());

        // Use different SQL depending on whether optional fields are present
        std::string sql =
            "INSERT INTO company_profiles (user_id, company_name, company_type, inn, industry, "
            "employees_count, annual_revenue, is_msp, created_at, updated_at) "
            "VALUES ($1, $2, $3, $4, $5, ";

        // Build the values portion for optional fields
        if (employees_count.has_value()) {
            sql += std::to_string(employees_count.value());
        } else {
            sql += "NULL";
        }
        sql += ", ";
        if (annual_revenue.has_value()) {
            sql += std::to_string(annual_revenue.value());
        } else {
            sql += "NULL";
        }
        sql += ", " + std::string(is_msp ? "true" : "false") + ", NOW(), NOW()) "
               "ON CONFLICT (user_id) DO UPDATE SET "
               "company_name = EXCLUDED.company_name, "
               "company_type = EXCLUDED.company_type, "
               "inn = EXCLUDED.inn, "
               "industry = EXCLUDED.industry, "
               "employees_count = EXCLUDED.employees_count, "
               "annual_revenue = EXCLUDED.annual_revenue, "
               "is_msp = EXCLUDED.is_msp, "
               "updated_at = NOW() "
               "RETURNING id, user_id, company_name, company_type, inn, ogrn, industry, "
               "employees_count, annual_revenue, is_msp, created_at, updated_at";

        auto result = txn.exec_params(sql,
            user_id, company_name, company_type, inn, industry);
        txn.commit();
        return parse_company_profile(result[0]);
    }

    // ========================================================================
    // TaxRegime operations
    // ========================================================================

    std::vector<TaxRegime> find_all_tax_regimes() {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec(
            "SELECT id, code, name, description, conditions, nk_ref "
            "FROM tax_regimes ORDER BY id");
        txn.commit();
        std::vector<TaxRegime> regimes;
        regimes.reserve(result.size());
        for (const auto& row : result) {
            regimes.push_back(parse_tax_regime(row));
        }
        return regimes;
    }

    std::optional<TaxRegime> find_tax_regime_by_code(const std::string& code) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, code, name, description, conditions, nk_ref "
            "FROM tax_regimes WHERE code = $1",
            code);
        txn.commit();
        if (result.empty()) return std::nullopt;
        return parse_tax_regime(result[0]);
    }

    std::optional<TaxRegime> find_tax_regime_by_id(int64_t id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, code, name, description, conditions, nk_ref "
            "FROM tax_regimes WHERE id = $1",
            id);
        txn.commit();
        if (result.empty()) return std::nullopt;
        return parse_tax_regime(result[0]);
    }

    TaxRegime save_tax_regime(const TaxRegime& regime) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        pqxx::result result;
        if (regime.id > 0) {
            result = txn.exec_params(
                "UPDATE tax_regimes SET code = $1, name = $2, description = $3, "
                "conditions = $4, nk_ref = $5 WHERE id = $6 "
                "RETURNING id, code, name, description, conditions, nk_ref",
                regime.code, regime.name, regime.description,
                regime.conditions, regime.nk_ref, regime.id);
        } else {
            result = txn.exec_params(
                "INSERT INTO tax_regimes (code, name, description, conditions, nk_ref) "
                "VALUES ($1, $2, $3, $4, $5) "
                "RETURNING id, code, name, description, conditions, nk_ref",
                regime.code, regime.name, regime.description,
                regime.conditions, regime.nk_ref);
        }
        txn.commit();
        return parse_tax_regime(result[0]);
    }

    // ========================================================================
    // CompanyTaxRegime operations
    // ========================================================================

    std::vector<CompanyTaxRegime> find_company_tax_regimes(int64_t company_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT ctr.id, ctr.company_id, ctr.tax_regime_id, ctr.is_current, "
            "ctr.applied_since, tr.code AS regime_code, tr.name AS regime_name "
            "FROM company_tax_regimes ctr "
            "JOIN tax_regimes tr ON tr.id = ctr.tax_regime_id "
            "WHERE ctr.company_id = $1 ORDER BY ctr.id",
            company_id);
        txn.commit();
        std::vector<CompanyTaxRegime> regimes;
        regimes.reserve(result.size());
        for (const auto& row : result) {
            regimes.push_back(parse_company_tax_regime(row));
        }
        return regimes;
    }

    std::vector<CompanyTaxRegime> find_current_regimes(int64_t company_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT ctr.id, ctr.company_id, ctr.tax_regime_id, ctr.is_current, "
            "ctr.applied_since, tr.code AS regime_code, tr.name AS regime_name "
            "FROM company_tax_regimes ctr "
            "JOIN tax_regimes tr ON tr.id = ctr.tax_regime_id "
            "WHERE ctr.company_id = $1 AND ctr.is_current = true ORDER BY ctr.id",
            company_id);
        txn.commit();
        std::vector<CompanyTaxRegime> regimes;
        regimes.reserve(result.size());
        for (const auto& row : result) {
            regimes.push_back(parse_company_tax_regime(row));
        }
        return regimes;
    }

    void clear_current_regimes(int64_t company_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        txn.exec_params(
            "UPDATE company_tax_regimes SET is_current = false WHERE company_id = $1",
            company_id);
        txn.commit();
    }

    void set_tax_regime(int64_t company_id, int64_t tax_regime_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        txn.exec_params(
            "INSERT INTO company_tax_regimes (company_id, tax_regime_id, is_current, applied_since) "
            "VALUES ($1, $2, true, NOW()) "
            "ON CONFLICT (company_id, tax_regime_id) DO UPDATE SET is_current = true",
            company_id, tax_regime_id);
        txn.commit();
    }

    // ========================================================================
    // TaxObligation operations
    // ========================================================================

    std::vector<TaxObligation> find_obligations_by_regime(int64_t tax_regime_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, tax_regime_id, tax_name, rate, description, nk_ref, fns_service_url "
            "FROM tax_obligations WHERE tax_regime_id = $1 ORDER BY id",
            tax_regime_id);
        txn.commit();
        std::vector<TaxObligation> obligations;
        obligations.reserve(result.size());
        for (const auto& row : result) {
            obligations.push_back(parse_tax_obligation(row));
        }
        return obligations;
    }

    // ========================================================================
    // Tender operations
    // ========================================================================

    std::optional<Tender> find_tender_by_id(int64_t id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, registry_number, title, description, customer_name, customer_inn, "
            "law_type, procurement_method, initial_price, currency, region, okpd_code, "
            "category, published_at, submission_deadline, auction_date, "
            "application_security, contract_security, msp_only, status, "
            "source_url, source, created_at "
            "FROM tenders WHERE id = $1",
            id);
        txn.commit();
        if (result.empty()) return std::nullopt;
        return parse_tender(result[0]);
    }

    std::optional<Tender> find_tender_by_registry_number(const std::string& number) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, registry_number, title, description, customer_name, customer_inn, "
            "law_type, procurement_method, initial_price, currency, region, okpd_code, "
            "category, published_at, submission_deadline, auction_date, "
            "application_security, contract_security, msp_only, status, "
            "source_url, source, created_at "
            "FROM tenders WHERE registry_number = $1",
            number);
        txn.commit();
        if (result.empty()) return std::nullopt;
        return parse_tender(result[0]);
    }

    int64_t count_tenders() {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec("SELECT COUNT(*) FROM tenders");
        txn.commit();
        return result[0][0].as<int64_t>();
    }

    int64_t count_tenders_by_status(const std::string& status) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT COUNT(*) FROM tenders WHERE status = $1",
            status);
        txn.commit();
        return result[0][0].as<int64_t>();
    }

    std::vector<Tender> search_tenders(const std::string& query,
                                        const std::string& law_type,
                                        const std::string& status,
                                        const std::string& region,
                                        const std::string& category,
                                        std::optional<double> price_from,
                                        std::optional<double> price_to,
                                        bool msp_only,
                                        int page,
                                        int size,
                                        const std::string& sort) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());

        std::string sql =
            "SELECT id, registry_number, title, description, customer_name, customer_inn, "
            "law_type, procurement_method, initial_price, currency, region, okpd_code, "
            "category, published_at, submission_deadline, auction_date, "
            "application_security, contract_security, msp_only, status, "
            "source_url, source, created_at "
            "FROM tenders WHERE 1=1";

        append_tender_filters(sql, txn, query, law_type, status, region,
                              category, price_from, price_to, msp_only);

        // Sort
        std::string order_clause = sort.empty() ? "published_at DESC" : sort;
        sql += " ORDER BY " + order_clause;

        // Pagination
        int offset = page * size;
        sql += " LIMIT " + std::to_string(size) + " OFFSET " + std::to_string(offset);

        auto result = txn.exec(sql);
        txn.commit();

        std::vector<Tender> tenders;
        tenders.reserve(result.size());
        for (const auto& row : result) {
            tenders.push_back(parse_tender(row));
        }
        return tenders;
    }

    int64_t count_search_results(const std::string& query,
                                  const std::string& law_type,
                                  const std::string& status,
                                  const std::string& region,
                                  const std::string& category,
                                  std::optional<double> price_from,
                                  std::optional<double> price_to,
                                  bool msp_only) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());

        std::string sql = "SELECT COUNT(*) FROM tenders WHERE 1=1";

        append_tender_filters(sql, txn, query, law_type, status, region,
                              category, price_from, price_to, msp_only);

        auto result = txn.exec(sql);
        txn.commit();
        return result[0][0].as<int64_t>();
    }

    std::vector<std::string> find_distinct_regions() {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec(
            "SELECT DISTINCT region FROM tenders "
            "WHERE region IS NOT NULL AND region != '' ORDER BY region");
        txn.commit();
        std::vector<std::string> regions;
        regions.reserve(result.size());
        for (const auto& row : result) {
            regions.push_back(row[0].as<std::string>());
        }
        return regions;
    }

    std::vector<std::string> find_distinct_categories() {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec(
            "SELECT DISTINCT category FROM tenders "
            "WHERE category IS NOT NULL AND category != '' ORDER BY category");
        txn.commit();
        std::vector<std::string> categories;
        categories.reserve(result.size());
        for (const auto& row : result) {
            categories.push_back(row[0].as<std::string>());
        }
        return categories;
    }

    void save_tender(const Tender& tender) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());

        std::string sql =
            "INSERT INTO tenders (registry_number, title, description, customer_name, "
            "customer_inn, law_type, procurement_method, initial_price, currency, region, "
            "okpd_code, category, published_at, submission_deadline, auction_date, "
            "application_security, contract_security, msp_only, status, source_url, "
            "source, created_at) VALUES (";
        sql += txn.quote(tender.registry_number) + ", ";
        sql += txn.quote(tender.title) + ", ";
        sql += txn.quote(tender.description) + ", ";
        sql += txn.quote(tender.customer_name) + ", ";
        sql += txn.quote(tender.customer_inn) + ", ";
        sql += txn.quote(tender.law_type) + ", ";
        sql += txn.quote(tender.procurement_method) + ", ";
        sql += (tender.initial_price.has_value() ? std::to_string(tender.initial_price.value()) : "NULL") + ", ";
        sql += txn.quote(tender.currency) + ", ";
        sql += txn.quote(tender.region) + ", ";
        sql += txn.quote(tender.okpd_code) + ", ";
        sql += txn.quote(tender.category) + ", ";
        sql += (tender.published_at.empty() ? "NULL" : txn.quote(tender.published_at)) + ", ";
        sql += (tender.submission_deadline.empty() ? "NULL" : txn.quote(tender.submission_deadline)) + ", ";
        sql += (tender.auction_date.empty() ? "NULL" : txn.quote(tender.auction_date)) + ", ";
        sql += (tender.application_security.has_value() ? std::to_string(tender.application_security.value()) : "NULL") + ", ";
        sql += (tender.contract_security.has_value() ? std::to_string(tender.contract_security.value()) : "NULL") + ", ";
        sql += (tender.msp_only ? "true" : "false") + std::string(", ");
        sql += txn.quote(tender.status) + ", ";
        sql += txn.quote(tender.source_url) + ", ";
        sql += txn.quote(tender.source) + ", ";
        sql += "NOW()";
        sql += ") ON CONFLICT (registry_number) DO NOTHING";

        txn.exec(sql);
        txn.commit();
    }

    std::vector<Tender> find_recent_tenders(int limit = 10) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, registry_number, title, description, customer_name, customer_inn, "
            "law_type, procurement_method, initial_price, currency, region, okpd_code, "
            "category, published_at, submission_deadline, auction_date, "
            "application_security, contract_security, msp_only, status, "
            "source_url, source, created_at "
            "FROM tenders ORDER BY published_at DESC NULLS LAST LIMIT $1",
            limit);
        txn.commit();
        std::vector<Tender> tenders;
        tenders.reserve(result.size());
        for (const auto& row : result) {
            tenders.push_back(parse_tender(row));
        }
        return tenders;
    }

    // ========================================================================
    // Deadline operations
    // ========================================================================

    std::vector<Deadline> find_template_deadlines(int64_t tax_regime_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, tax_regime_id, company_id, title, description, due_date, "
            "repeat_rule, is_custom, created_at "
            "FROM deadlines WHERE tax_regime_id = $1 AND company_id IS NULL ORDER BY due_date",
            tax_regime_id);
        txn.commit();
        std::vector<Deadline> deadlines;
        deadlines.reserve(result.size());
        for (const auto& row : result) {
            deadlines.push_back(parse_deadline(row));
        }
        return deadlines;
    }

    std::vector<Deadline> find_company_deadlines(int64_t company_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, tax_regime_id, company_id, title, description, due_date, "
            "repeat_rule, is_custom, created_at "
            "FROM deadlines WHERE company_id = $1 ORDER BY due_date",
            company_id);
        txn.commit();
        std::vector<Deadline> deadlines;
        deadlines.reserve(result.size());
        for (const auto& row : result) {
            deadlines.push_back(parse_deadline(row));
        }
        return deadlines;
    }

    std::vector<Deadline> find_upcoming_deadlines(int days_ahead) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        std::string interval_str = std::to_string(days_ahead) + " days";
        auto result = txn.exec_params(
            "SELECT id, tax_regime_id, company_id, title, description, due_date, "
            "repeat_rule, is_custom, created_at "
            "FROM deadlines WHERE due_date BETWEEN NOW() AND NOW() + $1::interval "
            "ORDER BY due_date",
            interval_str);
        txn.commit();
        std::vector<Deadline> deadlines;
        deadlines.reserve(result.size());
        for (const auto& row : result) {
            deadlines.push_back(parse_deadline(row));
        }
        return deadlines;
    }

    // ========================================================================
    // Notification operations
    // ========================================================================

    std::vector<Notification> find_user_notifications(int64_t user_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, user_id, deadline_id, title, message, is_read, send_at, created_at "
            "FROM notifications WHERE user_id = $1 ORDER BY created_at DESC",
            user_id);
        txn.commit();
        std::vector<Notification> notifications;
        notifications.reserve(result.size());
        for (const auto& row : result) {
            notifications.push_back(parse_notification(row));
        }
        return notifications;
    }

    std::vector<Notification> find_unread_notifications(int64_t user_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, user_id, deadline_id, title, message, is_read, send_at, created_at "
            "FROM notifications WHERE user_id = $1 AND is_read = false "
            "ORDER BY created_at DESC",
            user_id);
        txn.commit();
        std::vector<Notification> notifications;
        notifications.reserve(result.size());
        for (const auto& row : result) {
            notifications.push_back(parse_notification(row));
        }
        return notifications;
    }

    int64_t count_unread(int64_t user_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT COUNT(*) FROM notifications WHERE user_id = $1 AND is_read = false",
            user_id);
        txn.commit();
        return result[0][0].as<int64_t>();
    }

    void mark_as_read(int64_t id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        txn.exec_params(
            "UPDATE notifications SET is_read = true WHERE id = $1",
            id);
        txn.commit();
    }

    void mark_all_as_read(int64_t user_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        txn.exec_params(
            "UPDATE notifications SET is_read = true WHERE user_id = $1 AND is_read = false",
            user_id);
        txn.commit();
    }

    void delete_notification(int64_t id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        txn.exec_params("DELETE FROM notifications WHERE id = $1", id);
        txn.commit();
    }

    void save_notification(int64_t user_id, std::optional<int64_t> deadline_id,
                           const std::string& title, const std::string& message) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        if (deadline_id.has_value()) {
            txn.exec_params(
                "INSERT INTO notifications (user_id, deadline_id, title, message, is_read, send_at, created_at) "
                "VALUES ($1, $2, $3, $4, false, NOW(), NOW())",
                user_id, deadline_id.value(), title, message);
        } else {
            txn.exec_params(
                "INSERT INTO notifications (user_id, deadline_id, title, message, is_read, send_at, created_at) "
                "VALUES ($1, NULL, $2, $3, false, NOW(), NOW())",
                user_id, title, message);
        }
        txn.commit();
    }

    bool notification_exists_recent(int64_t user_id, int64_t deadline_id, int hours = 12) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        std::string interval_str = std::to_string(hours) + " hours";
        auto result = txn.exec_params(
            "SELECT COUNT(*) FROM notifications "
            "WHERE user_id = $1 AND deadline_id = $2 AND created_at > NOW() - $3::interval",
            user_id, deadline_id, interval_str);
        txn.commit();
        return result[0][0].as<int64_t>() > 0;
    }

    // ========================================================================
    // UserFavorite operations
    // ========================================================================

    std::vector<UserFavorite> find_user_favorites(int64_t user_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, user_id, entity_type, entity_id, created_at "
            "FROM user_favorites WHERE user_id = $1 ORDER BY created_at DESC",
            user_id);
        txn.commit();
        std::vector<UserFavorite> favorites;
        favorites.reserve(result.size());
        for (const auto& row : result) {
            favorites.push_back(parse_user_favorite(row));
        }
        return favorites;
    }

    std::vector<UserFavorite> find_user_favorites_by_type(int64_t user_id,
                                                          const std::string& entity_type) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, user_id, entity_type, entity_id, created_at "
            "FROM user_favorites WHERE user_id = $1 AND entity_type = $2 "
            "ORDER BY created_at DESC",
            user_id, entity_type);
        txn.commit();
        std::vector<UserFavorite> favorites;
        favorites.reserve(result.size());
        for (const auto& row : result) {
            favorites.push_back(parse_user_favorite(row));
        }
        return favorites;
    }

    bool is_favorite(int64_t user_id, const std::string& entity_type, int64_t entity_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT COUNT(*) FROM user_favorites "
            "WHERE user_id = $1 AND entity_type = $2 AND entity_id = $3",
            user_id, entity_type, entity_id);
        txn.commit();
        return result[0][0].as<int64_t>() > 0;
    }

    void add_favorite(int64_t user_id, const std::string& entity_type, int64_t entity_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        txn.exec_params(
            "INSERT INTO user_favorites (user_id, entity_type, entity_id, created_at) "
            "VALUES ($1, $2, $3, NOW())",
            user_id, entity_type, entity_id);
        txn.commit();
    }

    void remove_favorite(int64_t user_id, const std::string& entity_type, int64_t entity_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        txn.exec_params(
            "DELETE FROM user_favorites WHERE user_id = $1 AND entity_type = $2 AND entity_id = $3",
            user_id, entity_type, entity_id);
        txn.commit();
    }

    void remove_favorite_by_id(int64_t id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        txn.exec_params("DELETE FROM user_favorites WHERE id = $1", id);
        txn.commit();
    }

    // ========================================================================
    // ProcurementScenario operations
    // ========================================================================

    std::vector<ProcurementScenario> find_all_scenarios() {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec(
            "SELECT id, law_type, title, description, msp_only, amount_min, amount_max "
            "FROM procurement_scenarios ORDER BY id");
        txn.commit();
        std::vector<ProcurementScenario> scenarios;
        scenarios.reserve(result.size());
        for (const auto& row : result) {
            scenarios.push_back(parse_procurement_scenario(row));
        }
        return scenarios;
    }

    std::optional<ProcurementScenario> find_scenario_by_id(int64_t id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, law_type, title, description, msp_only, amount_min, amount_max "
            "FROM procurement_scenarios WHERE id = $1",
            id);
        txn.commit();
        if (result.empty()) return std::nullopt;
        return parse_procurement_scenario(result[0]);
    }

    std::vector<ProcurementScenario> find_msp_scenarios() {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec(
            "SELECT id, law_type, title, description, msp_only, amount_min, amount_max "
            "FROM procurement_scenarios WHERE msp_only = true ORDER BY id");
        txn.commit();
        std::vector<ProcurementScenario> scenarios;
        scenarios.reserve(result.size());
        for (const auto& row : result) {
            scenarios.push_back(parse_procurement_scenario(row));
        }
        return scenarios;
    }

    ProcurementScenario save_scenario(const ProcurementScenario& s) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        pqxx::result result;

        // Build SQL manually to handle optional fields
        if (s.id > 0) {
            std::string sql =
                "UPDATE procurement_scenarios SET law_type = " + txn.quote(s.law_type) +
                ", title = " + txn.quote(s.title) +
                ", description = " + txn.quote(s.description) +
                ", msp_only = " + std::string(s.msp_only ? "true" : "false") +
                ", amount_min = " + (s.amount_min.has_value() ? std::to_string(s.amount_min.value()) : "NULL") +
                ", amount_max = " + (s.amount_max.has_value() ? std::to_string(s.amount_max.value()) : "NULL") +
                " WHERE id = " + std::to_string(s.id) +
                " RETURNING id, law_type, title, description, msp_only, amount_min, amount_max";
            result = txn.exec(sql);
        } else {
            std::string sql =
                "INSERT INTO procurement_scenarios (law_type, title, description, msp_only, amount_min, amount_max) "
                "VALUES (" + txn.quote(s.law_type) + ", " +
                txn.quote(s.title) + ", " +
                txn.quote(s.description) + ", " +
                std::string(s.msp_only ? "true" : "false") + ", " +
                (s.amount_min.has_value() ? std::to_string(s.amount_min.value()) : "NULL") + ", " +
                (s.amount_max.has_value() ? std::to_string(s.amount_max.value()) : "NULL") +
                ") RETURNING id, law_type, title, description, msp_only, amount_min, amount_max";
            result = txn.exec(sql);
        }
        txn.commit();
        return parse_procurement_scenario(result[0]);
    }

    // ========================================================================
    // RiskCard operations
    // ========================================================================

    std::vector<RiskCard> find_risks_by_scenario(int64_t scenario_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, scenario_id, title, risk_type, description, consequence, recommendation "
            "FROM risk_cards WHERE scenario_id = $1 ORDER BY id",
            scenario_id);
        txn.commit();
        std::vector<RiskCard> risks;
        risks.reserve(result.size());
        for (const auto& row : result) {
            risks.push_back(parse_risk_card(row));
        }
        return risks;
    }

    std::vector<RiskCard> find_all_risks() {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec(
            "SELECT id, scenario_id, title, risk_type, description, consequence, recommendation "
            "FROM risk_cards ORDER BY scenario_id, id");
        txn.commit();
        std::vector<RiskCard> risks;
        risks.reserve(result.size());
        for (const auto& row : result) {
            risks.push_back(parse_risk_card(row));
        }
        return risks;
    }

    std::optional<RiskCard> find_risk_by_id(int64_t id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, scenario_id, title, risk_type, description, consequence, recommendation "
            "FROM risk_cards WHERE id = $1",
            id);
        txn.commit();
        if (result.empty()) return std::nullopt;
        return parse_risk_card(result[0]);
    }

    std::vector<RiskCard> find_risks_by_type(const std::string& risk_type) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, scenario_id, title, risk_type, description, consequence, recommendation "
            "FROM risk_cards WHERE risk_type = $1 ORDER BY id",
            risk_type);
        txn.commit();
        std::vector<RiskCard> risks;
        risks.reserve(result.size());
        for (const auto& row : result) {
            risks.push_back(parse_risk_card(row));
        }
        return risks;
    }

    RiskCard save_risk(const RiskCard& r) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        pqxx::result result;
        if (r.id > 0) {
            result = txn.exec_params(
                "UPDATE risk_cards SET scenario_id = $1, title = $2, risk_type = $3, "
                "description = $4, consequence = $5, recommendation = $6 WHERE id = $7 "
                "RETURNING id, scenario_id, title, risk_type, description, consequence, recommendation",
                r.scenario_id, r.title, r.risk_type,
                r.description, r.consequence, r.recommendation, r.id);
        } else {
            result = txn.exec_params(
                "INSERT INTO risk_cards (scenario_id, title, risk_type, description, consequence, recommendation) "
                "VALUES ($1, $2, $3, $4, $5, $6) "
                "RETURNING id, scenario_id, title, risk_type, description, consequence, recommendation",
                r.scenario_id, r.title, r.risk_type,
                r.description, r.consequence, r.recommendation);
        }
        txn.commit();
        return parse_risk_card(result[0]);
    }

    void delete_risk(int64_t id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        txn.exec_params("DELETE FROM risk_cards WHERE id = $1", id);
        txn.commit();
    }

    // ========================================================================
    // Checklist operations
    // ========================================================================

    std::vector<Checklist> find_user_checklists(int64_t user_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, user_id, scenario_id, title, description, is_template, "
            "is_completed, created_at, updated_at "
            "FROM checklists WHERE user_id = $1 ORDER BY created_at DESC",
            user_id);
        std::vector<Checklist> checklists;
        checklists.reserve(result.size());
        for (const auto& row : result) {
            Checklist c = parse_checklist(row);
            // Load steps for each checklist
            auto steps_result = txn.exec_params(
                "SELECT id, checklist_id, step_order, title, description, hint, "
                "is_completed, completed_at "
                "FROM checklist_steps WHERE checklist_id = $1 ORDER BY step_order",
                c.id);
            for (const auto& srow : steps_result) {
                c.steps.push_back(parse_checklist_step(srow));
            }
            checklists.push_back(std::move(c));
        }
        txn.commit();
        return checklists;
    }

    std::vector<Checklist> find_template_checklists() {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec(
            "SELECT id, user_id, scenario_id, title, description, is_template, "
            "is_completed, created_at, updated_at "
            "FROM checklists WHERE is_template = true ORDER BY id");
        std::vector<Checklist> checklists;
        checklists.reserve(result.size());
        for (const auto& row : result) {
            Checklist c = parse_checklist(row);
            auto steps_result = txn.exec_params(
                "SELECT id, checklist_id, step_order, title, description, hint, "
                "is_completed, completed_at "
                "FROM checklist_steps WHERE checklist_id = $1 ORDER BY step_order",
                c.id);
            for (const auto& srow : steps_result) {
                c.steps.push_back(parse_checklist_step(srow));
            }
            checklists.push_back(std::move(c));
        }
        txn.commit();
        return checklists;
    }

    std::optional<Checklist> find_checklist_by_id(int64_t id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, user_id, scenario_id, title, description, is_template, "
            "is_completed, created_at, updated_at "
            "FROM checklists WHERE id = $1",
            id);
        if (result.empty()) {
            txn.commit();
            return std::nullopt;
        }
        Checklist c = parse_checklist(result[0]);
        auto steps_result = txn.exec_params(
            "SELECT id, checklist_id, step_order, title, description, hint, "
            "is_completed, completed_at "
            "FROM checklist_steps WHERE checklist_id = $1 ORDER BY step_order",
            c.id);
        for (const auto& srow : steps_result) {
            c.steps.push_back(parse_checklist_step(srow));
        }
        txn.commit();
        return c;
    }

    Checklist save_checklist(const Checklist& c) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());

        // Build INSERT for the checklist with optional fields
        std::string sql =
            "INSERT INTO checklists (user_id, scenario_id, title, description, "
            "is_template, is_completed, created_at, updated_at) VALUES (";
        sql += (c.user_id.has_value() ? std::to_string(c.user_id.value()) : "NULL") + ", ";
        sql += (c.scenario_id.has_value() ? std::to_string(c.scenario_id.value()) : "NULL") + ", ";
        sql += txn.quote(c.title) + ", ";
        sql += txn.quote(c.description) + ", ";
        sql += std::string(c.is_template ? "true" : "false") + ", ";
        sql += std::string(c.is_completed ? "true" : "false") + ", ";
        sql += "NOW(), NOW()) RETURNING id, user_id, scenario_id, title, description, "
               "is_template, is_completed, created_at, updated_at";

        auto cl_result = txn.exec(sql);
        Checklist saved = parse_checklist(cl_result[0]);

        // Insert all steps
        for (const auto& step : c.steps) {
            std::string step_sql =
                "INSERT INTO checklist_steps (checklist_id, step_order, title, description, "
                "hint, is_completed, completed_at) VALUES (" +
                std::to_string(saved.id) + ", " +
                std::to_string(step.step_order) + ", " +
                txn.quote(step.title) + ", " +
                txn.quote(step.description) + ", " +
                txn.quote(step.hint) + ", " +
                std::string(step.is_completed ? "true" : "false") + ", ";
            if (step.completed_at.has_value()) {
                step_sql += txn.quote(step.completed_at.value());
            } else {
                step_sql += "NULL";
            }
            step_sql += ") RETURNING id, checklist_id, step_order, title, description, "
                        "hint, is_completed, completed_at";
            auto step_result = txn.exec(step_sql);
            saved.steps.push_back(parse_checklist_step(step_result[0]));
        }

        txn.commit();
        return saved;
    }

    void update_checklist_completion(int64_t id, bool completed) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        txn.exec_params(
            "UPDATE checklists SET is_completed = $1, updated_at = NOW() WHERE id = $2",
            completed, id);
        txn.commit();
    }

    // ========================================================================
    // ChecklistStep operations
    // ========================================================================

    std::vector<ChecklistStep> find_steps_by_checklist(int64_t checklist_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, checklist_id, step_order, title, description, hint, "
            "is_completed, completed_at "
            "FROM checklist_steps WHERE checklist_id = $1 ORDER BY step_order",
            checklist_id);
        txn.commit();
        std::vector<ChecklistStep> steps;
        steps.reserve(result.size());
        for (const auto& row : result) {
            steps.push_back(parse_checklist_step(row));
        }
        return steps;
    }

    std::optional<ChecklistStep> find_step_by_id(int64_t id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, checklist_id, step_order, title, description, hint, "
            "is_completed, completed_at "
            "FROM checklist_steps WHERE id = $1",
            id);
        txn.commit();
        if (result.empty()) return std::nullopt;
        return parse_checklist_step(result[0]);
    }

    void toggle_step(int64_t step_id, bool completed) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        if (completed) {
            txn.exec_params(
                "UPDATE checklist_steps SET is_completed = true, completed_at = NOW() "
                "WHERE id = $1",
                step_id);
        } else {
            txn.exec_params(
                "UPDATE checklist_steps SET is_completed = false, completed_at = NULL "
                "WHERE id = $1",
                step_id);
        }
        txn.commit();
    }

    // ========================================================================
    // LegalReference operations
    // ========================================================================

    std::vector<LegalReference> find_references(const std::string& entity_type, int64_t entity_id) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, entity_type, entity_id, title, url, article "
            "FROM legal_references WHERE entity_type = $1 AND entity_id = $2 ORDER BY id",
            entity_type, entity_id);
        txn.commit();
        std::vector<LegalReference> refs;
        refs.reserve(result.size());
        for (const auto& row : result) {
            refs.push_back(parse_legal_reference(row));
        }
        return refs;
    }

    std::vector<LegalReference> find_references_by_type(const std::string& entity_type) {
        ConnectionPool::ConnectionGuard guard(pool_);
        pqxx::work txn(guard.get());
        auto result = txn.exec_params(
            "SELECT id, entity_type, entity_id, title, url, article "
            "FROM legal_references WHERE entity_type = $1 ORDER BY id",
            entity_type);
        txn.commit();
        std::vector<LegalReference> refs;
        refs.reserve(result.size());
        for (const auto& row : result) {
            refs.push_back(parse_legal_reference(row));
        }
        return refs;
    }

private:
    ConnectionPool& pool_;

    // ========================================================================
    // Row parsers
    // ========================================================================

    static User parse_user(const pqxx::row& row) {
        User u;
        u.id        = row["id"].as<int64_t>();
        u.email     = row["email"].as<std::string>();
        u.password  = row["password"].as<std::string>();
        u.full_name = row["full_name"].as<std::string>();
        u.role      = role_from_string(row["role"].as<std::string>());
        u.created_at = row["created_at"].is_null() ? "" : row["created_at"].as<std::string>();
        u.updated_at = row["updated_at"].is_null() ? "" : row["updated_at"].as<std::string>();
        return u;
    }

    static CompanyProfile parse_company_profile(const pqxx::row& row) {
        CompanyProfile p;
        p.id               = row["id"].as<int64_t>();
        p.user_id          = row["user_id"].as<int64_t>();
        p.company_name     = row["company_name"].as<std::string>();
        p.company_type_str = row["company_type"].as<std::string>();
        p.inn              = row["inn"].is_null() ? "" : row["inn"].as<std::string>();
        p.ogrn             = row["ogrn"].is_null() ? "" : row["ogrn"].as<std::string>();
        p.industry         = row["industry"].is_null() ? "" : row["industry"].as<std::string>();
        if (!row["employees_count"].is_null()) {
            p.employees_count = row["employees_count"].as<int>();
        }
        if (!row["annual_revenue"].is_null()) {
            p.annual_revenue = row["annual_revenue"].as<double>();
        }
        p.is_msp     = row["is_msp"].as<bool>();
        p.created_at = row["created_at"].is_null() ? "" : row["created_at"].as<std::string>();
        p.updated_at = row["updated_at"].is_null() ? "" : row["updated_at"].as<std::string>();
        return p;
    }

    static TaxRegime parse_tax_regime(const pqxx::row& row) {
        TaxRegime r;
        r.id          = row["id"].as<int64_t>();
        r.code        = row["code"].as<std::string>();
        r.name        = row["name"].as<std::string>();
        r.description = row["description"].is_null() ? "" : row["description"].as<std::string>();
        r.conditions  = row["conditions"].is_null() ? "" : row["conditions"].as<std::string>();
        r.nk_ref      = row["nk_ref"].is_null() ? "" : row["nk_ref"].as<std::string>();
        return r;
    }

    static CompanyTaxRegime parse_company_tax_regime(const pqxx::row& row) {
        CompanyTaxRegime ctr;
        ctr.id             = row["id"].as<int64_t>();
        ctr.company_id     = row["company_id"].as<int64_t>();
        ctr.tax_regime_id  = row["tax_regime_id"].as<int64_t>();
        ctr.is_current     = row["is_current"].as<bool>();
        ctr.applied_since  = row["applied_since"].is_null() ? "" : row["applied_since"].as<std::string>();
        ctr.regime_code    = row["regime_code"].is_null() ? "" : row["regime_code"].as<std::string>();
        ctr.regime_name    = row["regime_name"].is_null() ? "" : row["regime_name"].as<std::string>();
        return ctr;
    }

    static TaxObligation parse_tax_obligation(const pqxx::row& row) {
        TaxObligation o;
        o.id              = row["id"].as<int64_t>();
        o.tax_regime_id   = row["tax_regime_id"].as<int64_t>();
        o.tax_name        = row["tax_name"].as<std::string>();
        o.rate            = row["rate"].is_null() ? "" : row["rate"].as<std::string>();
        o.description     = row["description"].is_null() ? "" : row["description"].as<std::string>();
        o.nk_ref          = row["nk_ref"].is_null() ? "" : row["nk_ref"].as<std::string>();
        o.fns_service_url = row["fns_service_url"].is_null() ? "" : row["fns_service_url"].as<std::string>();
        return o;
    }

    static Tender parse_tender(const pqxx::row& row) {
        Tender t;
        t.id                  = row["id"].as<int64_t>();
        t.registry_number     = row["registry_number"].is_null() ? "" : row["registry_number"].as<std::string>();
        t.title               = row["title"].is_null() ? "" : row["title"].as<std::string>();
        t.description         = row["description"].is_null() ? "" : row["description"].as<std::string>();
        t.customer_name       = row["customer_name"].is_null() ? "" : row["customer_name"].as<std::string>();
        t.customer_inn        = row["customer_inn"].is_null() ? "" : row["customer_inn"].as<std::string>();
        t.law_type            = row["law_type"].is_null() ? "" : row["law_type"].as<std::string>();
        t.procurement_method  = row["procurement_method"].is_null() ? "" : row["procurement_method"].as<std::string>();
        if (!row["initial_price"].is_null()) {
            t.initial_price = row["initial_price"].as<double>();
        }
        t.currency            = row["currency"].is_null() ? "" : row["currency"].as<std::string>();
        t.region              = row["region"].is_null() ? "" : row["region"].as<std::string>();
        t.okpd_code           = row["okpd_code"].is_null() ? "" : row["okpd_code"].as<std::string>();
        t.category            = row["category"].is_null() ? "" : row["category"].as<std::string>();
        t.published_at        = row["published_at"].is_null() ? "" : row["published_at"].as<std::string>();
        t.submission_deadline = row["submission_deadline"].is_null() ? "" : row["submission_deadline"].as<std::string>();
        t.auction_date        = row["auction_date"].is_null() ? "" : row["auction_date"].as<std::string>();
        if (!row["application_security"].is_null()) {
            t.application_security = row["application_security"].as<double>();
        }
        if (!row["contract_security"].is_null()) {
            t.contract_security = row["contract_security"].as<double>();
        }
        t.msp_only            = row["msp_only"].is_null() ? false : row["msp_only"].as<bool>();
        t.status              = row["status"].is_null() ? "" : row["status"].as<std::string>();
        t.source_url          = row["source_url"].is_null() ? "" : row["source_url"].as<std::string>();
        t.source              = row["source"].is_null() ? "" : row["source"].as<std::string>();
        t.created_at          = row["created_at"].is_null() ? "" : row["created_at"].as<std::string>();
        return t;
    }

    static Deadline parse_deadline(const pqxx::row& row) {
        Deadline d;
        d.id          = row["id"].as<int64_t>();
        if (!row["tax_regime_id"].is_null()) {
            d.tax_regime_id = row["tax_regime_id"].as<int64_t>();
        }
        if (!row["company_id"].is_null()) {
            d.company_id = row["company_id"].as<int64_t>();
        }
        d.title       = row["title"].as<std::string>();
        d.description = row["description"].is_null() ? "" : row["description"].as<std::string>();
        d.due_date    = row["due_date"].is_null() ? "" : row["due_date"].as<std::string>();
        d.repeat_rule = row["repeat_rule"].is_null() ? "NONE" : row["repeat_rule"].as<std::string>();
        d.is_custom   = row["is_custom"].is_null() ? false : row["is_custom"].as<bool>();
        d.created_at  = row["created_at"].is_null() ? "" : row["created_at"].as<std::string>();
        return d;
    }

    static Notification parse_notification(const pqxx::row& row) {
        Notification n;
        n.id        = row["id"].as<int64_t>();
        n.user_id   = row["user_id"].as<int64_t>();
        if (!row["deadline_id"].is_null()) {
            n.deadline_id = row["deadline_id"].as<int64_t>();
        }
        n.title     = row["title"].as<std::string>();
        n.message   = row["message"].is_null() ? "" : row["message"].as<std::string>();
        n.is_read   = row["is_read"].as<bool>();
        n.send_at   = row["send_at"].is_null() ? "" : row["send_at"].as<std::string>();
        n.created_at = row["created_at"].is_null() ? "" : row["created_at"].as<std::string>();
        return n;
    }

    static UserFavorite parse_user_favorite(const pqxx::row& row) {
        UserFavorite f;
        f.id          = row["id"].as<int64_t>();
        f.user_id     = row["user_id"].as<int64_t>();
        f.entity_type = row["entity_type"].as<std::string>();
        f.entity_id   = row["entity_id"].as<int64_t>();
        f.created_at  = row["created_at"].is_null() ? "" : row["created_at"].as<std::string>();
        return f;
    }

    static ProcurementScenario parse_procurement_scenario(const pqxx::row& row) {
        ProcurementScenario s;
        s.id          = row["id"].as<int64_t>();
        s.law_type    = row["law_type"].as<std::string>();
        s.title       = row["title"].as<std::string>();
        s.description = row["description"].is_null() ? "" : row["description"].as<std::string>();
        s.msp_only    = row["msp_only"].is_null() ? false : row["msp_only"].as<bool>();
        if (!row["amount_min"].is_null()) {
            s.amount_min = row["amount_min"].as<double>();
        }
        if (!row["amount_max"].is_null()) {
            s.amount_max = row["amount_max"].as<double>();
        }
        return s;
    }

    static RiskCard parse_risk_card(const pqxx::row& row) {
        RiskCard r;
        r.id             = row["id"].as<int64_t>();
        r.scenario_id    = row["scenario_id"].as<int64_t>();
        r.title          = row["title"].as<std::string>();
        r.risk_type      = row["risk_type"].as<std::string>();
        r.description    = row["description"].is_null() ? "" : row["description"].as<std::string>();
        r.consequence    = row["consequence"].is_null() ? "" : row["consequence"].as<std::string>();
        r.recommendation = row["recommendation"].is_null() ? "" : row["recommendation"].as<std::string>();
        return r;
    }

    static Checklist parse_checklist(const pqxx::row& row) {
        Checklist c;
        c.id           = row["id"].as<int64_t>();
        if (!row["user_id"].is_null()) {
            c.user_id = row["user_id"].as<int64_t>();
        }
        if (!row["scenario_id"].is_null()) {
            c.scenario_id = row["scenario_id"].as<int64_t>();
        }
        c.title        = row["title"].as<std::string>();
        c.description  = row["description"].is_null() ? "" : row["description"].as<std::string>();
        c.is_template  = row["is_template"].is_null() ? false : row["is_template"].as<bool>();
        c.is_completed = row["is_completed"].is_null() ? false : row["is_completed"].as<bool>();
        c.created_at   = row["created_at"].is_null() ? "" : row["created_at"].as<std::string>();
        c.updated_at   = row["updated_at"].is_null() ? "" : row["updated_at"].as<std::string>();
        return c;
    }

    static ChecklistStep parse_checklist_step(const pqxx::row& row) {
        ChecklistStep s;
        s.id           = row["id"].as<int64_t>();
        s.checklist_id = row["checklist_id"].as<int64_t>();
        s.step_order   = row["step_order"].as<int>();
        s.title        = row["title"].as<std::string>();
        s.description  = row["description"].is_null() ? "" : row["description"].as<std::string>();
        s.hint         = row["hint"].is_null() ? "" : row["hint"].as<std::string>();
        s.is_completed = row["is_completed"].is_null() ? false : row["is_completed"].as<bool>();
        if (!row["completed_at"].is_null()) {
            s.completed_at = row["completed_at"].as<std::string>();
        }
        return s;
    }

    static LegalReference parse_legal_reference(const pqxx::row& row) {
        LegalReference r;
        r.id          = row["id"].as<int64_t>();
        r.entity_type = row["entity_type"].as<std::string>();
        r.entity_id   = row["entity_id"].as<int64_t>();
        r.title       = row["title"].as<std::string>();
        r.url         = row["url"].is_null() ? "" : row["url"].as<std::string>();
        r.article     = row["article"].is_null() ? "" : row["article"].as<std::string>();
        return r;
    }

    // ========================================================================
    // Tender search filter builder
    // ========================================================================

    static void append_tender_filters(std::string& sql, pqxx::work& txn,
                                       const std::string& query,
                                       const std::string& law_type,
                                       const std::string& status,
                                       const std::string& region,
                                       const std::string& category,
                                       std::optional<double> price_from,
                                       std::optional<double> price_to,
                                       bool msp_only) {
        if (!query.empty()) {
            sql += " AND (title ILIKE '%" + txn.esc(query) + "%'"
                   " OR description ILIKE '%" + txn.esc(query) + "%')";
        }
        if (!law_type.empty()) {
            sql += " AND law_type = " + txn.quote(law_type);
        }
        if (!status.empty()) {
            sql += " AND status = " + txn.quote(status);
        }
        if (!region.empty()) {
            sql += " AND region = " + txn.quote(region);
        }
        if (!category.empty()) {
            sql += " AND category = " + txn.quote(category);
        }
        if (price_from.has_value()) {
            sql += " AND initial_price >= " + std::to_string(price_from.value());
        }
        if (price_to.has_value()) {
            sql += " AND initial_price <= " + std::to_string(price_to.value());
        }
        if (msp_only) {
            sql += " AND msp_only = true";
        }
    }
};
