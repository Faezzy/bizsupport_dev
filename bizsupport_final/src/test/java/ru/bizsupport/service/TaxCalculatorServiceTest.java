package ru.bizsupport.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.bizsupport.service.TaxCalculatorService.*;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TaxCalculatorService — юнит-тесты")
class TaxCalculatorServiceTest {

    private final TaxCalculatorService calculator = new TaxCalculatorService();

    @Test
    @DisplayName("ИП без сотрудников, доход 1 млн → включает НПД")
    void ipSmallIncludesNpd() {
        TaxCalcInput input = TaxCalcInput.builder()
                .revenue(BigDecimal.valueOf(1_000_000))
                .expenses(BigDecimal.valueOf(300_000))
                .employees(0).avgSalary(BigDecimal.ZERO).ip(true).build();

        List<TaxCalcResult> results = calculator.calculate(input);
        assertThat(results).extracting(TaxCalcResult::getRegimeCode).contains("NPD");
    }

    @Test
    @DisplayName("ООО → НПД и ПСН не предлагаются")
    void oooExcludesNpdPsn() {
        TaxCalcInput input = TaxCalcInput.builder()
                .revenue(BigDecimal.valueOf(5_000_000))
                .expenses(BigDecimal.valueOf(2_000_000))
                .employees(10).avgSalary(BigDecimal.valueOf(50_000)).ip(false).build();

        List<TaxCalcResult> results = calculator.calculate(input);
        assertThat(results).extracting(TaxCalcResult::getRegimeCode)
                .doesNotContain("NPD", "PSN");
    }

    @Test
    @DisplayName("Результаты отсортированы по totalLoad (возрастание)")
    void resultsSortedByLoad() {
        TaxCalcInput input = TaxCalcInput.builder()
                .revenue(BigDecimal.valueOf(3_000_000))
                .expenses(BigDecimal.valueOf(1_500_000))
                .employees(2).avgSalary(BigDecimal.valueOf(40_000)).ip(true).build();

        List<TaxCalcResult> results = calculator.calculate(input);
        for (int i = 1; i < results.size(); i++) {
            assertThat(results.get(i).getTotalLoad())
                    .isGreaterThanOrEqualTo(results.get(i - 1).getTotalLoad());
        }
    }

    @Test
    @DisplayName("Первый результат помечен как best")
    void firstIsBest() {
        TaxCalcInput input = TaxCalcInput.builder()
                .revenue(BigDecimal.valueOf(2_000_000))
                .expenses(BigDecimal.valueOf(500_000))
                .employees(0).avgSalary(BigDecimal.ZERO).ip(true).build();

        List<TaxCalcResult> results = calculator.calculate(input);
        assertThat(results.get(0).getBest()).isTrue();
        results.stream().skip(1).forEach(r -> assertThat(r.getBest()).isFalse());
    }

    @Test
    @DisplayName("УСН 6% — вычет взносов 100% для ИП без работников")
    void usn6FullDeductionForSoloIp() {
        TaxCalcInput input = TaxCalcInput.builder()
                .revenue(BigDecimal.valueOf(1_000_000))
                .expenses(BigDecimal.ZERO)
                .employees(0).avgSalary(BigDecimal.ZERO).ip(true).build();

        List<TaxCalcResult> results = calculator.calculate(input);
        TaxCalcResult usn6 = results.stream()
                .filter(r -> "USN_6".equals(r.getRegimeCode())).findFirst().orElseThrow();

        // Налог 6% = 60000, взносы ~60658, вычет = мин(60658, 60000) = 60000
        // Налог после вычета = 0, итого = 0 + 60658 = 60658
        assertThat(usn6.getTotalLoad()).isLessThan(BigDecimal.valueOf(70_000));
    }

    @Test
    @DisplayName("УСН 15% — минимальный налог 1% при убытке")
    void usn15MinTaxOnLoss() {
        TaxCalcInput input = TaxCalcInput.builder()
                .revenue(BigDecimal.valueOf(1_000_000))
                .expenses(BigDecimal.valueOf(990_000))  // почти весь доход — расходы
                .employees(0).avgSalary(BigDecimal.ZERO).ip(true).build();

        List<TaxCalcResult> results = calculator.calculate(input);
        TaxCalcResult usn15 = results.stream()
                .filter(r -> "USN_15".equals(r.getRegimeCode())).findFirst().orElseThrow();

        // Прибыль = 10000, 15% = 1500, но мин. налог 1% = 10000 → применяется 10000
        assertThat(usn15.getTaxAmount()).isGreaterThanOrEqualTo(BigDecimal.valueOf(10_000));
    }

    @Test
    @DisplayName("ОСНО — включает НДС и налог на прибыль/НДФЛ")
    void osnoHasNdsAndIncomeTax() {
        TaxCalcInput input = TaxCalcInput.builder()
                .revenue(BigDecimal.valueOf(5_000_000))
                .expenses(BigDecimal.valueOf(3_000_000))
                .employees(5).avgSalary(BigDecimal.valueOf(60_000)).ip(false).build();

        List<TaxCalcResult> results = calculator.calculate(input);
        TaxCalcResult osno = results.stream()
                .filter(r -> "OSNO".equals(r.getRegimeCode())).findFirst().orElseThrow();

        assertThat(osno.getDetails()).containsKey("НДС (20%)");
        assertThat(osno.getDetails()).containsKey("Налог на прибыль (20%)");
        assertThat(osno.getTotalLoad()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("effectiveRate — не превышает 100%")
    void effectiveRateSane() {
        TaxCalcInput input = TaxCalcInput.builder()
                .revenue(BigDecimal.valueOf(500_000))
                .expenses(BigDecimal.valueOf(100_000))
                .employees(0).avgSalary(BigDecimal.ZERO).ip(true).build();

        List<TaxCalcResult> results = calculator.calculate(input);
        results.forEach(r ->
                assertThat(r.getEffectiveRate()).isLessThanOrEqualTo(BigDecimal.valueOf(100)));
    }

    @Test
    @DisplayName("getTotalLoadFormatted — возвращает строку с ₽")
    void formattedOutput() {
        TaxCalcInput input = TaxCalcInput.builder()
                .revenue(BigDecimal.valueOf(1_000_000))
                .expenses(BigDecimal.ZERO)
                .employees(0).avgSalary(BigDecimal.ZERO).ip(true).build();

        List<TaxCalcResult> results = calculator.calculate(input);
        results.forEach(r -> assertThat(r.getTotalLoadFormatted()).contains("₽"));
    }
}
