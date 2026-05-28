#pragma once
#include "models.hpp"
#include <nlohmann/json.hpp>
#include <string>
#include <vector>
#include <algorithm>
#include <cmath>
#include <sstream>
#include <iomanip>
#include <locale>

// ============================================================================
// Input / Output structs
// ============================================================================

struct TaxCalcInput {
    double revenue = 0;
    double expenses = 0;
    int employees = 0;
    double avg_salary = 0;
    bool is_ip = true; // true = IP (individual entrepreneur), false = OOO
};

struct TaxCalcResult {
    std::string regime_code;
    std::string regime_name;
    double tax_amount = 0;
    double contributions = 0;
    double total_load = 0;
    double effective_rate = 0;
    std::vector<std::string> breakdown;
    bool applicable = true;
    std::string not_applicable_reason;
    bool best = false;
};

// ============================================================================
// TaxCalculatorService
// ============================================================================

class TaxCalculatorService {
public:
    // Calculate tax load across all available regimes
    std::vector<TaxCalcResult> calculate(const TaxCalcInput& input) {
        std::vector<TaxCalcResult> results;

        results.push_back(calc_usn6(input));
        results.push_back(calc_usn15(input));
        results.push_back(calc_osno(input));
        results.push_back(calc_npd(input));
        results.push_back(calc_psn(input));

        // Sort applicable results by total_load ascending
        std::sort(results.begin(), results.end(), [](const TaxCalcResult& a, const TaxCalcResult& b) {
            // Non-applicable go to the end
            if (a.applicable != b.applicable) return a.applicable > b.applicable;
            return a.total_load < b.total_load;
        });

        // Mark best (first applicable)
        for (auto& r : results) {
            if (r.applicable) {
                r.best = true;
                break;
            }
        }

        return results;
    }

private:
    // ── Constants ────────────────────────────────────────────────
    static constexpr double IP_FIXED_CONTRIBUTIONS = 53658.0;
    static constexpr double IP_EXTRA_THRESHOLD = 300000.0;
    static constexpr double IP_EXTRA_RATE = 0.01;
    static constexpr double IP_MAX_CONTRIBUTIONS = 300888.0;
    static constexpr double EMPLOYEE_INSURANCE_RATE = 0.302;

    // ── USN 6% (Income) ─────────────────────────────────────────
    TaxCalcResult calc_usn6(const TaxCalcInput& input) {
        TaxCalcResult result;
        result.regime_code = "USN_6";
        result.regime_name = "\xD0\xA3\xD0\xA1\xD0\x9D \xC2\xAB\xD0\x94\xD0\xBE\xD1\x85\xD0\xBE\xD0\xB4\xD1\x8B\xC2\xBB (6%)"; // "УСН «Доходы» (6%)"

        // Check applicability
        if (input.revenue > 265800000.0 || input.employees > 130) {
            result.applicable = false;
            result.not_applicable_reason = "\xD0\x9F\xD1\x80\xD0\xB5\xD0\xB2\xD1\x8B\xD1\x88\xD0\xB5\xD0\xBD\xD1\x8B "
                "\xD0\xBB\xD0\xB8\xD0\xBC\xD0\xB8\xD1\x82\xD1\x8B "
                "\xD0\xA3\xD0\xA1\xD0\x9D: \xD0\xB4\xD0\xBE\xD1\x85\xD0\xBE\xD0\xB4 "
                "\xD0\xB4\xD0\xBE 265.8 \xD0\xBC\xD0\xBB\xD0\xBD, "
                "\xD1\x81\xD0\xBE\xD1\x82\xD1\x80\xD1\x83\xD0\xB4\xD0\xBD\xD0\xB8\xD0\xBA\xD0\xBE\xD0\xB2 "
                "\xD0\xB4\xD0\xBE 130"; // "Превышены лимиты УСН: доход до 265.8 млн, сотрудников до 130"
            return result;
        }

        double ip_contributions = input.is_ip ? calc_ip_contributions(input.revenue) : 0.0;
        double employee_insurance = calc_employee_insurance(input);
        double total_contributions = ip_contributions + employee_insurance;

        double tax = input.revenue * 0.06;

        // Deduction: 100% for IP without employees, 50% otherwise
        double deduction;
        if (input.is_ip && input.employees == 0) {
            deduction = std::min(tax, ip_contributions);
        } else {
            deduction = std::min(tax * 0.5, total_contributions);
        }

        double tax_after_deduction = std::max(tax - deduction, 0.0);
        double total_load = tax_after_deduction + total_contributions;

        result.tax_amount = round2(tax_after_deduction);
        result.contributions = round2(total_contributions);
        result.total_load = round2(total_load);
        result.effective_rate = calc_rate(total_load, input.revenue);

        // Breakdown
        // "Налог до вычета"
        result.breakdown.push_back(
            "\xD0\x9D\xD0\xB0\xD0\xBB\xD0\xBE\xD0\xB3 \xD0\xB4\xD0\xBE "
            "\xD0\xB2\xD1\x8B\xD1\x87\xD0\xB5\xD1\x82\xD0\xB0: " + format_money(tax));
        // "Вычет взносов"
        result.breakdown.push_back(
            "\xD0\x92\xD1\x8B\xD1\x87\xD0\xB5\xD1\x82 "
            "\xD0\xB2\xD0\xB7\xD0\xBD\xD0\xBE\xD1\x81\xD0\xBE\xD0\xB2: " + format_money(deduction));
        // "Налог после вычета"
        result.breakdown.push_back(
            "\xD0\x9D\xD0\xB0\xD0\xBB\xD0\xBE\xD0\xB3 \xD0\xBF\xD0\xBE\xD1\x81\xD0\xBB\xD0\xB5 "
            "\xD0\xB2\xD1\x8B\xD1\x87\xD0\xB5\xD1\x82\xD0\xB0: " + format_money(tax_after_deduction));
        // "Страховые взносы"
        result.breakdown.push_back(
            "\xD0\xA1\xD1\x82\xD1\x80\xD0\xB0\xD1\x85\xD0\xBE\xD0\xB2\xD1\x8B\xD0\xB5 "
            "\xD0\xB2\xD0\xB7\xD0\xBD\xD0\xBE\xD1\x81\xD1\x8B: " + format_money(total_contributions));
        if (input.is_ip) {
            // "  - Взносы ИП за себя"
            result.breakdown.push_back(
                "  - \xD0\x92\xD0\xB7\xD0\xBD\xD0\xBE\xD1\x81\xD1\x8B "
                "\xD0\x98\xD0\x9F \xD0\xB7\xD0\xB0 \xD1\x81\xD0\xB5\xD0\xB1\xD1\x8F: " + format_money(ip_contributions));
        }
        if (input.employees > 0) {
            // "  - Взносы за сотрудников"
            result.breakdown.push_back(
                "  - \xD0\x92\xD0\xB7\xD0\xBD\xD0\xBE\xD1\x81\xD1\x8B "
                "\xD0\xB7\xD0\xB0 \xD1\x81\xD0\xBE\xD1\x82\xD1\x80\xD1\x83\xD0\xB4\xD0\xBD\xD0\xB8\xD0\xBA\xD0\xBE\xD0\xB2: " + format_money(employee_insurance));
        }

        return result;
    }

    // ── USN 15% (Income - Expenses) ──────────────────────────────
    TaxCalcResult calc_usn15(const TaxCalcInput& input) {
        TaxCalcResult result;
        result.regime_code = "USN_15";
        result.regime_name = "\xD0\xA3\xD0\xA1\xD0\x9D \xC2\xAB\xD0\x94\xD0\xBE\xD1\x85\xD0\xBE\xD0\xB4\xD1\x8B "
            "\xE2\x88\x92 \xD0\xA0\xD0\xB0\xD1\x81\xD1\x85\xD0\xBE\xD0\xB4\xD1\x8B\xC2\xBB (15%)"; // "УСН «Доходы − Расходы» (15%)"

        // Check applicability
        if (input.revenue > 265800000.0 || input.employees > 130) {
            result.applicable = false;
            result.not_applicable_reason = "\xD0\x9F\xD1\x80\xD0\xB5\xD0\xB2\xD1\x8B\xD1\x88\xD0\xB5\xD0\xBD\xD1\x8B "
                "\xD0\xBB\xD0\xB8\xD0\xBC\xD0\xB8\xD1\x82\xD1\x8B "
                "\xD0\xA3\xD0\xA1\xD0\x9D: \xD0\xB4\xD0\xBE\xD1\x85\xD0\xBE\xD0\xB4 "
                "\xD0\xB4\xD0\xBE 265.8 \xD0\xBC\xD0\xBB\xD0\xBD, "
                "\xD1\x81\xD0\xBE\xD1\x82\xD1\x80\xD1\x83\xD0\xB4\xD0\xBD\xD0\xB8\xD0\xBA\xD0\xBE\xD0\xB2 "
                "\xD0\xB4\xD0\xBE 130";
            return result;
        }

        double ip_contributions = input.is_ip ? calc_ip_contributions(input.revenue) : 0.0;
        double employee_insurance = calc_employee_insurance(input);
        double total_contributions = ip_contributions + employee_insurance;

        double profit = std::max(input.revenue - input.expenses, 0.0);
        double tax = profit * 0.15;
        double min_tax = input.revenue * 0.01;
        bool is_min_tax = min_tax > tax;
        double actual_tax = std::max(tax, min_tax);

        double total_load = actual_tax + total_contributions;

        result.tax_amount = round2(actual_tax);
        result.contributions = round2(total_contributions);
        result.total_load = round2(total_load);
        result.effective_rate = calc_rate(total_load, input.revenue);

        // Breakdown
        // "Доходы - Расходы"
        result.breakdown.push_back(
            "\xD0\x94\xD0\xBE\xD1\x85\xD0\xBE\xD0\xB4\xD1\x8B \xe2\x88\x92 "
            "\xD0\xA0\xD0\xB0\xD1\x81\xD1\x85\xD0\xBE\xD0\xB4\xD1\x8B: " + format_money(profit));
        // "Налог 15%"
        result.breakdown.push_back(
            "\xD0\x9D\xD0\xB0\xD0\xBB\xD0\xBE\xD0\xB3 15%: " + format_money(tax));
        if (is_min_tax) {
            // "Мин. налог 1% (применён)"
            result.breakdown.push_back(
                "\xD0\x9C\xD0\xB8\xD0\xBD. \xD0\xBD\xD0\xB0\xD0\xBB\xD0\xBE\xD0\xB3 1%: "
                + format_money(min_tax) + " (\xD0\xBF\xD1\x80\xD0\xB8\xD0\xBC\xD0\xB5\xD0\xBD\xD1\x91\xD0\xBD)");
        }
        // "Страховые взносы"
        result.breakdown.push_back(
            "\xD0\xA1\xD1\x82\xD1\x80\xD0\xB0\xD1\x85\xD0\xBE\xD0\xB2\xD1\x8B\xD0\xB5 "
            "\xD0\xB2\xD0\xB7\xD0\xBD\xD0\xBE\xD1\x81\xD1\x8B: " + format_money(total_contributions));
        if (input.is_ip) {
            result.breakdown.push_back(
                "  - \xD0\x92\xD0\xB7\xD0\xBD\xD0\xBE\xD1\x81\xD1\x8B "
                "\xD0\x98\xD0\x9F \xD0\xB7\xD0\xB0 \xD1\x81\xD0\xB5\xD0\xB1\xD1\x8F: " + format_money(ip_contributions));
        }
        if (input.employees > 0) {
            result.breakdown.push_back(
                "  - \xD0\x92\xD0\xB7\xD0\xBD\xD0\xBE\xD1\x81\xD1\x8B "
                "\xD0\xB7\xD0\xB0 \xD1\x81\xD0\xBE\xD1\x82\xD1\x80\xD1\x83\xD0\xB4\xD0\xBD\xD0\xB8\xD0\xBA\xD0\xBE\xD0\xB2: " + format_money(employee_insurance));
        }

        return result;
    }

    // ── OSNO (General taxation system) ───────────────────────────
    TaxCalcResult calc_osno(const TaxCalcInput& input) {
        TaxCalcResult result;
        result.regime_code = "OSNO";
        result.regime_name = "\xD0\x9E\xD0\xA1\xD0\x9D\xD0\x9E \xe2\x80\x94 "
            "\xD0\x9E\xD0\xB1\xD1\x89\xD0\xB0\xD1\x8F \xD1\x81\xD0\xB8\xD1\x81\xD1\x82\xD0\xB5\xD0\xBC\xD0\xB0"; // "ОСНО -- Общая система"
        result.applicable = true; // Always applicable

        double ip_contributions = input.is_ip ? calc_ip_contributions(input.revenue) : 0.0;
        double employee_insurance = calc_employee_insurance(input);
        double total_contributions = ip_contributions + employee_insurance;

        // VAT: 20% extracted from revenue (revenue includes VAT)
        double nds = input.revenue * 20.0 / 120.0;

        double profit = std::max(input.revenue - input.expenses, 0.0);
        double income_tax;
        std::string income_tax_label;
        if (input.is_ip) {
            // NDFL 13%
            income_tax = profit * 0.13;
            income_tax_label = "\xD0\x9D\xD0\x94\xD0\xA4\xD0\x9B (13%)"; // "НДФЛ (13%)"
        } else {
            // Profit tax 20%
            income_tax = profit * 0.20;
            income_tax_label = "\xD0\x9D\xD0\xB0\xD0\xBB\xD0\xBE\xD0\xB3 \xD0\xBD\xD0\xB0 "
                "\xD0\xBF\xD1\x80\xD0\xB8\xD0\xB1\xD1\x8B\xD0\xBB\xD1\x8C (20%)"; // "Налог на прибыль (20%)"
        }

        double total_load = nds + income_tax + total_contributions;

        result.tax_amount = round2(nds + income_tax);
        result.contributions = round2(total_contributions);
        result.total_load = round2(total_load);
        result.effective_rate = calc_rate(total_load, input.revenue);

        // Breakdown
        // "НДС (20%)"
        result.breakdown.push_back(
            "\xD0\x9D\xD0\x94\xD0\xA1 (20%): " + format_money(nds));
        result.breakdown.push_back(income_tax_label + ": " + format_money(income_tax));
        // "Страховые взносы"
        result.breakdown.push_back(
            "\xD0\xA1\xD1\x82\xD1\x80\xD0\xB0\xD1\x85\xD0\xBE\xD0\xB2\xD1\x8B\xD0\xB5 "
            "\xD0\xB2\xD0\xB7\xD0\xBD\xD0\xBE\xD1\x81\xD1\x8B: " + format_money(total_contributions));
        if (input.is_ip) {
            result.breakdown.push_back(
                "  - \xD0\x92\xD0\xB7\xD0\xBD\xD0\xBE\xD1\x81\xD1\x8B "
                "\xD0\x98\xD0\x9F \xD0\xB7\xD0\xB0 \xD1\x81\xD0\xB5\xD0\xB1\xD1\x8F: " + format_money(ip_contributions));
        }
        if (input.employees > 0) {
            result.breakdown.push_back(
                "  - \xD0\x92\xD0\xB7\xD0\xBD\xD0\xBE\xD1\x81\xD1\x8B "
                "\xD0\xB7\xD0\xB0 \xD1\x81\xD0\xBE\xD1\x82\xD1\x80\xD1\x83\xD0\xB4\xD0\xBD\xD0\xB8\xD0\xBA\xD0\xBE\xD0\xB2: " + format_money(employee_insurance));
        }

        return result;
    }

    // ── NPD (Self-employed tax) ──────────────────────────────────
    TaxCalcResult calc_npd(const TaxCalcInput& input) {
        TaxCalcResult result;
        result.regime_code = "NPD";
        result.regime_name = "\xD0\x9D\xD0\x9F\xD0\x94 \xe2\x80\x94 "
            "\xD0\xA1\xD0\xB0\xD0\xBC\xD0\xBE\xD0\xB7\xD0\xB0\xD0\xBD\xD1\x8F\xD1\x82\xD1\x8B\xD0\xB9"; // "НПД -- Самозанятый"

        // Check applicability: only IP, no employees, revenue <= 2.4M
        if (!input.is_ip || input.employees > 0 || input.revenue > 2400000.0) {
            result.applicable = false;
            // "Только для ИП без сотрудников с доходом до 2.4 млн"
            result.not_applicable_reason = "\xD0\xA2\xD0\xBE\xD0\xBB\xD1\x8C\xD0\xBA\xD0\xBE "
                "\xD0\xB4\xD0\xBB\xD1\x8F \xD0\x98\xD0\x9F "
                "\xD0\xB1\xD0\xB5\xD0\xB7 \xD1\x81\xD0\xBE\xD1\x82\xD1\x80\xD1\x83\xD0\xB4\xD0\xBD\xD0\xB8\xD0\xBA\xD0\xBE\xD0\xB2 "
                "\xD1\x81 \xD0\xB4\xD0\xBE\xD1\x85\xD0\xBE\xD0\xB4\xD0\xBE\xD0\xBC "
                "\xD0\xB4\xD0\xBE 2.4 \xD0\xBC\xD0\xBB\xD0\xBD";
            return result;
        }

        // Simplified: ~5% average (4% from individuals, 6% from legal entities)
        double tax = input.revenue * 0.06;

        result.tax_amount = round2(tax);
        result.contributions = 0.0;
        result.total_load = round2(tax);
        result.effective_rate = calc_rate(tax, input.revenue);

        // Breakdown
        // "Налог НПД (6%)"
        result.breakdown.push_back(
            "\xD0\x9D\xD0\xB0\xD0\xBB\xD0\xBE\xD0\xB3 \xD0\x9D\xD0\x9F\xD0\x94 (6%): " + format_money(tax));
        // "Взносы: Не обязательны"
        result.breakdown.push_back(
            "\xD0\x92\xD0\xB7\xD0\xBD\xD0\xBE\xD1\x81\xD1\x8B: "
            "\xD0\x9D\xD0\xB5 \xD0\xBE\xD0\xB1\xD1\x8F\xD0\xB7\xD0\xB0\xD1\x82\xD0\xB5\xD0\xBB\xD1\x8C\xD0\xBD\xD1\x8B");

        return result;
    }

    // ── PSN (Patent taxation system) ─────────────────────────────
    TaxCalcResult calc_psn(const TaxCalcInput& input) {
        TaxCalcResult result;
        result.regime_code = "PSN";
        result.regime_name = "\xD0\x9F\xD0\xA1\xD0\x9D \xe2\x80\x94 "
            "\xD0\x9F\xD0\xB0\xD1\x82\xD0\xB5\xD0\xBD\xD1\x82"; // "ПСН -- Патент"

        // Check applicability: only IP, employees <= 15, revenue <= 60M
        if (!input.is_ip || input.employees > 15 || input.revenue > 60000000.0) {
            result.applicable = false;
            // "Только для ИП с числом сотрудников до 15 и доходом до 60 млн"
            result.not_applicable_reason = "\xD0\xA2\xD0\xBE\xD0\xBB\xD1\x8C\xD0\xBA\xD0\xBE "
                "\xD0\xB4\xD0\xBB\xD1\x8F \xD0\x98\xD0\x9F "
                "\xD1\x81 \xD1\x87\xD0\xB8\xD1\x81\xD0\xBB\xD0\xBE\xD0\xBC "
                "\xD1\x81\xD0\xBE\xD1\x82\xD1\x80\xD1\x83\xD0\xB4\xD0\xBD\xD0\xB8\xD0\xBA\xD0\xBE\xD0\xB2 "
                "\xD0\xB4\xD0\xBE 15 \xD0\xB8 \xD0\xB4\xD0\xBE\xD1\x85\xD0\xBE\xD0\xB4\xD0\xBE\xD0\xBC "
                "\xD0\xB4\xD0\xBE 60 \xD0\xBC\xD0\xBB\xD0\xBD";
            return result;
        }

        // Patent cost based on potential income (capped at 1M by spec, Java uses 1.5M)
        double potential_income = std::min(input.revenue, 1000000.0);
        double patent_cost = potential_income * 0.06;
        double ip_contributions = calc_ip_contributions(input.revenue);
        double total_load = patent_cost + ip_contributions;

        result.tax_amount = round2(patent_cost);
        result.contributions = round2(ip_contributions);
        result.total_load = round2(total_load);
        result.effective_rate = calc_rate(total_load, input.revenue);

        // Breakdown
        // "Стоимость патента"
        result.breakdown.push_back(
            "\xD0\xA1\xD1\x82\xD0\xBE\xD0\xB8\xD0\xBC\xD0\xBE\xD1\x81\xD1\x82\xD1\x8C "
            "\xD0\xBF\xD0\xB0\xD1\x82\xD0\xB5\xD0\xBD\xD1\x82\xD0\xB0: " + format_money(patent_cost));
        // "Страховые взносы ИП"
        result.breakdown.push_back(
            "\xD0\xA1\xD1\x82\xD1\x80\xD0\xB0\xD1\x85\xD0\xBE\xD0\xB2\xD1\x8B\xD0\xB5 "
            "\xD0\xB2\xD0\xB7\xD0\xBD\xD0\xBE\xD1\x81\xD1\x8B \xD0\x98\xD0\x9F: " + format_money(ip_contributions));
        // "Примечание: Зависит от региона и вида деятельности"
        result.breakdown.push_back(
            "\xD0\x9F\xD1\x80\xD0\xB8\xD0\xBC\xD0\xB5\xD1\x87\xD0\xB0\xD0\xBD\xD0\xB8\xD0\xB5: "
            "\xD0\x97\xD0\xB0\xD0\xB2\xD0\xB8\xD1\x81\xD0\xB8\xD1\x82 \xD0\xBE\xD1\x82 "
            "\xD1\x80\xD0\xB5\xD0\xB3\xD0\xB8\xD0\xBE\xD0\xBD\xD0\xB0 \xD0\xB8 "
            "\xD0\xB2\xD0\xB8\xD0\xB4\xD0\xB0 "
            "\xD0\xB4\xD0\xB5\xD1\x8F\xD1\x82\xD0\xB5\xD0\xBB\xD1\x8C\xD0\xBD\xD0\xBE\xD1\x81\xD1\x82\xD0\xB8");

        return result;
    }

    // ── Helper: IP fixed contributions ───────────────────────────
    double calc_ip_contributions(double revenue) {
        double fixed = IP_FIXED_CONTRIBUTIONS;
        double extra = 0.0;
        if (revenue > IP_EXTRA_THRESHOLD) {
            extra = (revenue - IP_EXTRA_THRESHOLD) * IP_EXTRA_RATE;
        }
        return std::min(fixed + extra, IP_MAX_CONTRIBUTIONS);
    }

    // ── Helper: employee insurance contributions ─────────────────
    double calc_employee_insurance(const TaxCalcInput& input) {
        if (input.employees <= 0 || input.avg_salary <= 0) return 0.0;
        double annual_payroll = input.avg_salary * 12.0 * input.employees;
        return annual_payroll * EMPLOYEE_INSURANCE_RATE;
    }

    // ── Helper: effective rate ────────────────────────────────────
    double calc_rate(double load, double revenue) {
        if (revenue <= 0.0) return 0.0;
        return round1((load / revenue) * 100.0);
    }

    // ── Helper: format money (Russian rubles) ────────────────────
    static std::string format_money(double amount) {
        // Format with thousand separators and ruble sign
        int64_t rounded = static_cast<int64_t>(std::round(amount));
        bool negative = rounded < 0;
        if (negative) rounded = -rounded;

        std::string digits = std::to_string(rounded);
        std::string formatted;

        int count = 0;
        for (int i = static_cast<int>(digits.size()) - 1; i >= 0; --i) {
            if (count > 0 && count % 3 == 0) {
                formatted = " " + formatted;
            }
            formatted = digits[i] + formatted;
            ++count;
        }

        if (negative) formatted = "-" + formatted;
        return formatted + " \xe2\x82\xbd"; // " ₽"
    }

    static double round2(double v) {
        return std::round(v * 100.0) / 100.0;
    }

    static double round1(double v) {
        return std::round(v * 10.0) / 10.0;
    }
};
