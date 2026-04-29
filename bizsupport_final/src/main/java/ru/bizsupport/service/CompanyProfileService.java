package ru.bizsupport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bizsupport.dto.request.CompanyProfileRequest;
import ru.bizsupport.entity.*;
import ru.bizsupport.repository.*;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CompanyProfileService {

    private final CompanyProfileRepository profileRepo;
    private final UserRepository userRepo;
    private final TaxRegimeRepository taxRegimeRepo;
    private final CompanyTaxRegimeRepository companyTaxRegimeRepo;

    public Optional<CompanyProfile> findByUserId(Long userId) {
        return profileRepo.findByUserId(userId);
    }

    @Transactional
    public CompanyProfile saveOrUpdate(Long userId, CompanyProfileRequest req) {
        User user = userRepo.findById(userId).orElseThrow();
        CompanyProfile profile = profileRepo.findByUserId(userId)
                .orElse(CompanyProfile.builder().user(user).build());

        profile.setCompanyName(req.getCompanyName());
        profile.setCompanyType(req.getCompanyType());
        profile.setInn(req.getInn());
        profile.setIndustry(req.getIndustry());
        profile.setEmployeesCount(req.getEmployeesCount());
        profile.setAnnualRevenue(req.getAnnualRevenue());

        // Автоопределение статуса МСП (упрощённое)
        profile.setIsMsp(isMsp(req));

        return profileRepo.save(profile);
    }

    @Transactional
    public void setTaxRegime(Long companyId, String regimeCode) {
        CompanyProfile profile = profileRepo.findById(companyId).orElseThrow();
        TaxRegime regime = taxRegimeRepo.findByCode(regimeCode).orElseThrow();

        // Снять флаг текущего с остальных
        companyTaxRegimeRepo.findByCompanyProfileId(companyId)
                .forEach(ctr -> { ctr.setIsCurrent(false); companyTaxRegimeRepo.save(ctr); });

        // Добавить новый
        CompanyTaxRegime ctr = CompanyTaxRegime.builder()
                .companyProfile(profile)
                .taxRegime(regime)
                .isCurrent(true)
                .appliedSince(java.time.LocalDate.now())
                .build();
        companyTaxRegimeRepo.save(ctr);
    }

    public List<CompanyTaxRegime> getCurrentRegimes(Long companyId) {
        return companyTaxRegimeRepo.findByCompanyProfileIdAndIsCurrentTrue(companyId);
    }

    // Простая проверка критериев МСП (ст. 4 ФЗ №209-ФЗ)
    private boolean isMsp(CompanyProfileRequest req) {
        if (req.getEmployeesCount() == null || req.getAnnualRevenue() == null) return false;
        return req.getEmployeesCount() <= 250 &&
               req.getAnnualRevenue().compareTo(java.math.BigDecimal.valueOf(2_000_000_000)) <= 0;
    }
}
