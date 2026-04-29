package ru.bizsupport.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.bizsupport.entity.CompanyTaxRegime;
import java.util.List;

@Repository
public interface CompanyTaxRegimeRepository extends JpaRepository<CompanyTaxRegime, Long> {
    List<CompanyTaxRegime> findByCompanyProfileId(Long companyId);
    List<CompanyTaxRegime> findByCompanyProfileIdAndIsCurrentTrue(Long companyId);
    void deleteByCompanyProfileId(Long companyId);
}
