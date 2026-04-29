package ru.bizsupport.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.bizsupport.entity.ProcurementScenario;
import java.util.List;

@Repository
public interface ProcurementScenarioRepository extends JpaRepository<ProcurementScenario, Long> {
    List<ProcurementScenario> findByLawType(ProcurementScenario.LawType lawType);
    List<ProcurementScenario> findByMspOnlyTrue();
}
