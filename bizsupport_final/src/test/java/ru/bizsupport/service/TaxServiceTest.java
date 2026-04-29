package ru.bizsupport.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.bizsupport.entity.*;
import ru.bizsupport.repository.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaxService — юнит-тесты")
class TaxServiceTest {

    @Mock private TaxRegimeRepository taxRegimeRepo;
    @Mock private TaxObligationRepository taxObligationRepo;
    @Mock private DeadlineRepository deadlineRepo;
    @Mock private CompanyTaxRegimeRepository companyTaxRegimeRepo;

    @InjectMocks private TaxService taxService;

    private TaxRegime usn6, usn15, osno, psn, npd;

    @BeforeEach
    void setUp() {
        usn6 = TaxRegime.builder().id(1L).code("USN_6").name("УСН 6%").build();
        usn15 = TaxRegime.builder().id(2L).code("USN_15").name("УСН 15%").build();
        osno = TaxRegime.builder().id(3L).code("OSNO").name("ОСНО").build();
        psn = TaxRegime.builder().id(4L).code("PSN").name("ПСН").build();
        npd = TaxRegime.builder().id(5L).code("NPD").name("НПД").build();
    }

    @Test
    @DisplayName("getAllRegimes — возвращает все режимы")
    void getAllRegimes() {
        when(taxRegimeRepo.findAll()).thenReturn(List.of(usn6, usn15, osno, psn, npd));
        List<TaxRegime> result = taxService.getAllRegimes();
        assertThat(result).hasSize(5);
        verify(taxRegimeRepo).findAll();
    }

    @Test
    @DisplayName("findByCode — находит режим по коду")
    void findByCode() {
        when(taxRegimeRepo.findByCode("USN_6")).thenReturn(Optional.of(usn6));
        Optional<TaxRegime> result = taxService.findByCode("USN_6");
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("УСН 6%");
    }

    @Test
    @DisplayName("findByCode — возвращает empty для несуществующего кода")
    void findByCodeNotFound() {
        when(taxRegimeRepo.findByCode("FAKE")).thenReturn(Optional.empty());
        assertThat(taxService.findByCode("FAKE")).isEmpty();
    }

    @Test
    @DisplayName("recommend — ИП без сотрудников, доход <2.4 млн → все 5 режимов")
    void recommendIpSmall() {
        when(taxRegimeRepo.findAll()).thenReturn(List.of(usn6, usn15, osno, psn, npd));
        List<TaxRegime> result = taxService.recommend(
                CompanyProfile.CompanyType.IP, 0, BigDecimal.valueOf(2_000_000));
        assertThat(result).extracting(TaxRegime::getCode)
                .contains("USN_6", "USN_15", "OSNO", "PSN", "NPD");
    }

    @Test
    @DisplayName("recommend — ООО, 50 сотрудников → УСН + ОСНО, без ПСН/НПД")
    void recommendOoo() {
        when(taxRegimeRepo.findAll()).thenReturn(List.of(usn6, usn15, osno, psn, npd));
        List<TaxRegime> result = taxService.recommend(
                CompanyProfile.CompanyType.OOO, 50, BigDecimal.valueOf(100_000_000));
        assertThat(result).extracting(TaxRegime::getCode)
                .contains("USN_6", "USN_15", "OSNO")
                .doesNotContain("PSN", "NPD");
    }

    @Test
    @DisplayName("recommend — ИП, 200 сотрудников, доход 300 млн → только ОСНО")
    void recommendIpLarge() {
        when(taxRegimeRepo.findAll()).thenReturn(List.of(usn6, usn15, osno, psn, npd));
        List<TaxRegime> result = taxService.recommend(
                CompanyProfile.CompanyType.IP, 200, BigDecimal.valueOf(300_000_000));
        assertThat(result).extracting(TaxRegime::getCode)
                .containsExactly("OSNO");
    }

    @Test
    @DisplayName("recommend — ИП, 10 сотрудников → ПСН доступен, НПД нет")
    void recommendPsnAvailable() {
        when(taxRegimeRepo.findAll()).thenReturn(List.of(usn6, usn15, osno, psn, npd));
        List<TaxRegime> result = taxService.recommend(
                CompanyProfile.CompanyType.IP, 10, BigDecimal.valueOf(30_000_000));
        assertThat(result).extracting(TaxRegime::getCode)
                .contains("PSN")
                .doesNotContain("NPD");
    }

    @Test
    @DisplayName("getObligationsForRegime — делегирует в репозиторий")
    void getObligations() {
        TaxObligation ob = TaxObligation.builder().id(1L).taxName("Налог").build();
        when(taxObligationRepo.findByTaxRegimeId(1L)).thenReturn(List.of(ob));
        List<TaxObligation> result = taxService.getObligationsForRegime(1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTaxName()).isEqualTo("Налог");
    }
}
