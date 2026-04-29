package ru.bizsupport.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.bizsupport.dto.request.CompanyProfileRequest;
import ru.bizsupport.entity.*;
import ru.bizsupport.repository.*;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompanyProfileService — юнит-тесты")
class CompanyProfileServiceTest {

    @Mock private CompanyProfileRepository profileRepo;
    @Mock private UserRepository userRepo;
    @Mock private TaxRegimeRepository taxRegimeRepo;
    @Mock private CompanyTaxRegimeRepository companyTaxRegimeRepo;

    @InjectMocks private CompanyProfileService profileService;

    @Test
    @DisplayName("saveOrUpdate — малый бизнес автоматически получает статус МСП")
    void saveOrUpdateMsp() {
        User user = User.builder().id(1L).email("test@test.ru").build();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(profileRepo.findByUserId(1L)).thenReturn(Optional.empty());
        when(profileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompanyProfileRequest req = new CompanyProfileRequest();
        req.setCompanyName("ООО Тест");
        req.setCompanyType(CompanyProfile.CompanyType.OOO);
        req.setEmployeesCount(50);
        req.setAnnualRevenue(BigDecimal.valueOf(100_000_000));

        CompanyProfile result = profileService.saveOrUpdate(1L, req);
        assertThat(result.getIsMsp()).isTrue();
    }

    @Test
    @DisplayName("saveOrUpdate — крупный бизнес НЕ получает статус МСП")
    void saveOrUpdateNotMsp() {
        User user = User.builder().id(1L).email("test@test.ru").build();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(profileRepo.findByUserId(1L)).thenReturn(Optional.empty());
        when(profileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompanyProfileRequest req = new CompanyProfileRequest();
        req.setCompanyName("ПАО Гигант");
        req.setCompanyType(CompanyProfile.CompanyType.OOO);
        req.setEmployeesCount(500);
        req.setAnnualRevenue(BigDecimal.valueOf(5_000_000_000L));

        CompanyProfile result = profileService.saveOrUpdate(1L, req);
        assertThat(result.getIsMsp()).isFalse();
    }

    @Test
    @DisplayName("findByUserId — делегирует в репозиторий")
    void findByUserId() {
        CompanyProfile profile = CompanyProfile.builder().id(1L).companyName("Тест").build();
        when(profileRepo.findByUserId(1L)).thenReturn(Optional.of(profile));

        Optional<CompanyProfile> result = profileService.findByUserId(1L);
        assertThat(result).isPresent();
        assertThat(result.get().getCompanyName()).isEqualTo("Тест");
    }

    @Test
    @DisplayName("saveOrUpdate — без revenue/employees → не МСП")
    void saveOrUpdateNullFieldsNotMsp() {
        User user = User.builder().id(1L).email("test@test.ru").build();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(profileRepo.findByUserId(1L)).thenReturn(Optional.empty());
        when(profileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompanyProfileRequest req = new CompanyProfileRequest();
        req.setCompanyName("ИП Тест");
        req.setCompanyType(CompanyProfile.CompanyType.IP);
        // employees и revenue не заданы

        CompanyProfile result = profileService.saveOrUpdate(1L, req);
        assertThat(result.getIsMsp()).isFalse();
    }
}
