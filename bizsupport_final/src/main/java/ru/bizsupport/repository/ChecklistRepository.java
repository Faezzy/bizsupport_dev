package ru.bizsupport.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.bizsupport.entity.Checklist;
import java.util.List;

@Repository
public interface ChecklistRepository extends JpaRepository<Checklist, Long> {
    List<Checklist> findByUserId(Long userId);
    List<Checklist> findByIsTemplateTrue();
    List<Checklist> findByUserIdAndIsCompletedFalse(Long userId);
}
