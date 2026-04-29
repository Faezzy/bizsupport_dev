package ru.bizsupport.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.bizsupport.entity.RiskCard;
import java.util.List;

@Repository
public interface RiskCardRepository extends JpaRepository<RiskCard, Long> {
    List<RiskCard> findByScenarioId(Long scenarioId);
    List<RiskCard> findByRiskType(RiskCard.RiskType riskType);
}
