package ru.bizsupport.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.bizsupport.entity.ChecklistStep;
import java.util.List;

@Repository
public interface ChecklistStepRepository extends JpaRepository<ChecklistStep, Long> {
    List<ChecklistStep> findByChecklistIdOrderByStepOrderAsc(Long checklistId);
}
