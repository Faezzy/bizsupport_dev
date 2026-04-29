package ru.bizsupport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.bizsupport.entity.*;
import ru.bizsupport.repository.*;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TaxService {

    private final TaxRegimeRepository taxRegimeRepo;
    private final TaxObligationRepository taxObligationRepo;
    private final DeadlineRepository deadlineRepo;
    private final CompanyTaxRegimeRepository companyTaxRegimeRepo;

    public List<TaxRegime> getAllRegimes() {
        return taxRegimeRepo.findAll();
    }

    public Optional<TaxRegime> findByCode(String code) {
        return taxRegimeRepo.findByCode(code);
    }

    public List<TaxObligation> getObligationsForRegime(Long regimeId) {
        return taxObligationRepo.findByTaxRegimeId(regimeId);
    }

    public List<Deadline> getTemplateDeadlines(Long regimeId) {
        return deadlineRepo.findByTaxRegimeIdAndCompanyProfileIsNull(regimeId);
    }

    public List<Deadline> getCompanyDeadlines(Long companyId) {
        return deadlineRepo.findByCompanyProfileId(companyId);
    }

    /**
     * Подбор подходящих режимов по параметрам компании.
     * Простая реализация на основе ограничений НК РФ.
     */
    public List<TaxRegime> recommend(CompanyProfile.CompanyType type,
                                     Integer employees,
                                     BigDecimal revenue) {
        List<TaxRegime> all = taxRegimeRepo.findAll();
        List<TaxRegime> result = new ArrayList<>();

        for (TaxRegime r : all) {
            if (matches(r, type, employees, revenue)) {
                result.add(r);
            }
        }
        return result;
    }

    private boolean matches(TaxRegime regime,
                            CompanyProfile.CompanyType type,
                            Integer employees,
                            BigDecimal revenue) {
        // ОСНО — для всех
        if ("OSNO".equals(regime.getCode())) return true;

        // НПД — только ИП, без сотрудников, доход до 2.4 млн
        if ("NPD".equals(regime.getCode())) {
            return type == CompanyProfile.CompanyType.IP
                    && (employees == null || employees == 0)
                    && (revenue == null || revenue.compareTo(BigDecimal.valueOf(2_400_000)) <= 0);
        }

        // ПСН — только ИП, до 15 чел., доход до 60 млн
        if ("PSN".equals(regime.getCode())) {
            return type == CompanyProfile.CompanyType.IP
                    && (employees == null || employees <= 15)
                    && (revenue == null || revenue.compareTo(BigDecimal.valueOf(60_000_000)) <= 0);
        }

        // УСН — ИП и ООО, до 130 чел., доход до 265.8 млн
        if (regime.getCode().startsWith("USN")) {
            return (employees == null || employees <= 130)
                    && (revenue == null || revenue.compareTo(BigDecimal.valueOf(265_800_000)) <= 0);
        }

        return false;
    }

    public List<CompanyTaxRegime> getCompanyCurrentRegimes(Long companyId) {
        return companyTaxRegimeRepo.findByCompanyProfileIdAndIsCurrentTrue(companyId);
    }
}
