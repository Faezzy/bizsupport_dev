package ru.bizsupport.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.bizsupport.entity.TaxObligation;
import java.util.List;

@Repository
public interface TaxObligationRepository extends JpaRepository<TaxObligation, Long> {
    List<TaxObligation> findByTaxRegimeId(Long taxRegimeId);
}
