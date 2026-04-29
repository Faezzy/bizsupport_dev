package ru.bizsupport.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.bizsupport.entity.TaxRegime;
import java.util.Optional;

@Repository
public interface TaxRegimeRepository extends JpaRepository<TaxRegime, Long> {
    Optional<TaxRegime> findByCode(String code);
}
