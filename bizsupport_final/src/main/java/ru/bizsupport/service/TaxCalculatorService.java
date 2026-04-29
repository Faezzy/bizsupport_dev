package ru.bizsupport.service;

import lombok.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class TaxCalculatorService {

    // Фиксированные взносы ИП за себя (2025)
    private static final BigDecimal IP_FIXED_CONTRIBUTIONS = BigDecimal.valueOf(53_658);
    // 1% с дохода > 300 000
    private static final BigDecimal IP_EXTRA_THRESHOLD = BigDecimal.valueOf(300_000);
    private static final BigDecimal IP_EXTRA_RATE = new BigDecimal("0.01");
    // Максимум взносов
    private static final BigDecimal IP_MAX_CONTRIBUTIONS = BigDecimal.valueOf(300_888);

    // Ставки страховых взносов за сотрудников (суммарно ~30.2%)
    private static final BigDecimal EMPLOYEE_INSURANCE_RATE = new BigDecimal("0.302");

    /**
     * Рассчитать налоговую нагрузку по всем доступным режимам.
     */
    public List<TaxCalcResult> calculate(TaxCalcInput input) {
        List<TaxCalcResult> results = new ArrayList<>();

        results.add(calcUSN6(input));
        results.add(calcUSN15(input));
        results.add(calcOSNO(input));

        if (input.isIp()) {
            if (input.getEmployees() == 0 &&
                input.getRevenue().compareTo(BigDecimal.valueOf(2_400_000)) <= 0) {
                results.add(calcNPD(input));
            }
            if (input.getEmployees() <= 15 &&
                input.getRevenue().compareTo(BigDecimal.valueOf(60_000_000)) <= 0) {
                results.add(calcPSN(input));
            }
        }

        // Сортировка по итоговой нагрузке
        results.sort(Comparator.comparing(TaxCalcResult::getTotalLoad));

        // Пометка лучшего
        if (!results.isEmpty()) {
            results.get(0).setBest(true);
        }

        return results;
    }

    // ── УСН 6% (Доходы) ──────────────────────────────────────
    private TaxCalcResult calcUSN6(TaxCalcInput input) {
        BigDecimal tax = input.getRevenue().multiply(new BigDecimal("0.06"));
        BigDecimal contributions = calcContributions(input);

        // Вычет взносов из налога (до 50% для ООО/ИП с работниками, 100% для ИП без)
        BigDecimal deduction;
        if (input.isIp() && input.getEmployees() == 0) {
            deduction = contributions.min(tax);
        } else {
            deduction = contributions.min(tax.multiply(new BigDecimal("0.5")));
        }
        BigDecimal taxAfterDeduction = tax.subtract(deduction).max(BigDecimal.ZERO);

        BigDecimal totalLoad = taxAfterDeduction.add(contributions);

        return TaxCalcResult.builder()
                .regimeCode("USN_6")
                .regimeName("УСН «Доходы» (6%)")
                .taxAmount(taxAfterDeduction)
                .contributions(contributions)
                .totalLoad(totalLoad)
                .effectiveRate(calcRate(totalLoad, input.getRevenue()))
                .details(orderedMap(
                        "Налог до вычета", formatMoney(tax),
                        "Вычет взносов", formatMoney(deduction),
                        "Налог после вычета", formatMoney(taxAfterDeduction),
                        "Страховые взносы", formatMoney(contributions)
                ))
                .build();
    }

    // ── УСН 15% (Доходы − Расходы) ───────────────────────────
    private TaxCalcResult calcUSN15(TaxCalcInput input) {
        BigDecimal profit = input.getRevenue().subtract(input.getExpenses()).max(BigDecimal.ZERO);
        BigDecimal tax = profit.multiply(new BigDecimal("0.15"));
        BigDecimal minTax = input.getRevenue().multiply(new BigDecimal("0.01"));

        // Минимальный налог 1% от дохода
        BigDecimal actualTax = tax.max(minTax);
        boolean isMinTax = actualTax.compareTo(tax) > 0;

        BigDecimal contributions = calcContributions(input);
        BigDecimal totalLoad = actualTax.add(contributions);

        Map<String, String> details = new LinkedHashMap<>();
        details.put("Доходы − Расходы", formatMoney(profit));
        details.put("Налог 15%", formatMoney(tax));
        if (isMinTax) {
            details.put("Мин. налог 1%", formatMoney(minTax) + " (применён)");
        }
        details.put("Страховые взносы", formatMoney(contributions));

        return TaxCalcResult.builder()
                .regimeCode("USN_15")
                .regimeName("УСН «Доходы − Расходы» (15%)")
                .taxAmount(actualTax)
                .contributions(contributions)
                .totalLoad(totalLoad)
                .effectiveRate(calcRate(totalLoad, input.getRevenue()))
                .details(details)
                .build();
    }

    // ── ОСНО ──────────────────────────────────────────────────
    private TaxCalcResult calcOSNO(TaxCalcInput input) {
        BigDecimal profit = input.getRevenue().subtract(input.getExpenses()).max(BigDecimal.ZERO);
        BigDecimal contributions = calcContributions(input);

        // НДС (20% сверху, но упрощённо: ~16.67% от выручки с НДС)
        BigDecimal nds = input.getRevenue().multiply(new BigDecimal("20"))
                .divide(new BigDecimal("120"), 2, RoundingMode.HALF_UP);

        BigDecimal incomeTax;
        String incomeTaxLabel;
        if (input.isIp()) {
            // НДФЛ 13%
            incomeTax = profit.multiply(new BigDecimal("0.13"));
            incomeTaxLabel = "НДФЛ (13%)";
        } else {
            // Налог на прибыль 20%
            incomeTax = profit.multiply(new BigDecimal("0.20"));
            incomeTaxLabel = "Налог на прибыль (20%)";
        }

        BigDecimal totalLoad = nds.add(incomeTax).add(contributions);

        return TaxCalcResult.builder()
                .regimeCode("OSNO")
                .regimeName("ОСНО — Общая система")
                .taxAmount(nds.add(incomeTax))
                .contributions(contributions)
                .totalLoad(totalLoad)
                .effectiveRate(calcRate(totalLoad, input.getRevenue()))
                .details(orderedMap(
                        "НДС (20%)", formatMoney(nds),
                        incomeTaxLabel, formatMoney(incomeTax),
                        "Страховые взносы", formatMoney(contributions)
                ))
                .build();
    }

    // ── НПД ───────────────────────────────────────────────────
    private TaxCalcResult calcNPD(TaxCalcInput input) {
        // Упрощённо: 4% от физлиц, 6% от юрлиц → берём среднюю 5%
        BigDecimal tax = input.getRevenue().multiply(new BigDecimal("0.05"));

        return TaxCalcResult.builder()
                .regimeCode("NPD")
                .regimeName("НПД — Самозанятый")
                .taxAmount(tax)
                .contributions(BigDecimal.ZERO)
                .totalLoad(tax)
                .effectiveRate(calcRate(tax, input.getRevenue()))
                .details(orderedMap(
                        "Налог НПД (~5%)", formatMoney(tax),
                        "Взносы", "Не обязательны"
                ))
                .build();
    }

    // ── ПСН ───────────────────────────────────────────────────
    private TaxCalcResult calcPSN(TaxCalcInput input) {
        // Примерный расчёт: стоимость патента ≈ потенциальный доход × 6%
        // Потенциальный доход зависит от региона, берём примерно
        BigDecimal potentialIncome = BigDecimal.valueOf(
                Math.min(input.getRevenue().longValue(), 1_500_000));
        BigDecimal patentCost = potentialIncome.multiply(new BigDecimal("0.06"));
        BigDecimal contributions = calcIPContributions(input.getRevenue());
        BigDecimal totalLoad = patentCost.add(contributions);

        return TaxCalcResult.builder()
                .regimeCode("PSN")
                .regimeName("ПСН — Патент")
                .taxAmount(patentCost)
                .contributions(contributions)
                .totalLoad(totalLoad)
                .effectiveRate(calcRate(totalLoad, input.getRevenue()))
                .details(orderedMap(
                        "Стоимость патента", formatMoney(patentCost),
                        "Страховые взносы ИП", formatMoney(contributions),
                        "Примечание", "Зависит от региона и вида деятельности"
                ))
                .build();
    }

    // ── Вспомогательные ───────────────────────────────────────

    private BigDecimal calcContributions(TaxCalcInput input) {
        BigDecimal total = BigDecimal.ZERO;

        // ИП: фикс + 1% с дохода > 300к
        if (input.isIp()) {
            total = total.add(calcIPContributions(input.getRevenue()));
        }

        // За сотрудников: ~30.2% от ФОТ
        if (input.getEmployees() > 0 && input.getAvgSalary() != null) {
            BigDecimal annualPayroll = input.getAvgSalary()
                    .multiply(BigDecimal.valueOf(12))
                    .multiply(BigDecimal.valueOf(input.getEmployees()));
            total = total.add(annualPayroll.multiply(EMPLOYEE_INSURANCE_RATE));
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcIPContributions(BigDecimal revenue) {
        BigDecimal fixed = IP_FIXED_CONTRIBUTIONS;
        BigDecimal extra = BigDecimal.ZERO;
        if (revenue.compareTo(IP_EXTRA_THRESHOLD) > 0) {
            extra = revenue.subtract(IP_EXTRA_THRESHOLD).multiply(IP_EXTRA_RATE);
        }
        return fixed.add(extra).min(IP_MAX_CONTRIBUTIONS);
    }

    private BigDecimal calcRate(BigDecimal load, BigDecimal revenue) {
        if (revenue.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return load.multiply(BigDecimal.valueOf(100))
                .divide(revenue, 1, RoundingMode.HALF_UP);
    }

    private String formatMoney(BigDecimal amount) {
        return String.format("%,.0f ₽", amount);
    }

    /** Создаёт LinkedHashMap с гарантированным порядком ключей */
    private Map<String, String> orderedMap(String... keysAndValues) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length - 1; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    // ── DTO ───────────────────────────────────────────────────

    @Data @Builder
    public static class TaxCalcInput {
        private BigDecimal revenue;        // Годовой доход
        private BigDecimal expenses;       // Годовые расходы
        private int employees;             // Кол-во сотрудников
        private BigDecimal avgSalary;      // Средняя зарплата
        private boolean ip;                // ИП или ООО
    }

    @Data @Builder
    public static class TaxCalcResult {
        private String regimeCode;
        private String regimeName;
        private BigDecimal taxAmount;      // Сумма налогов
        private BigDecimal contributions;  // Страховые взносы
        private BigDecimal totalLoad;      // Итого нагрузка
        private BigDecimal effectiveRate;  // Эффективная ставка %
        private Map<String, String> details;
        @Builder.Default
        private Boolean best = false;

        /** Форматированная итоговая нагрузка для шаблонов */
        public String getTotalLoadFormatted() {
            if (totalLoad == null) return "0 ₽";
            return String.format("%,.0f ₽", totalLoad);
        }
    }
}
