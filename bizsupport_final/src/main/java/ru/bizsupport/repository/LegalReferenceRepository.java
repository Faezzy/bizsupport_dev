package ru.bizsupport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.bizsupport.entity.EntityType;
import ru.bizsupport.entity.LegalReference;

import java.util.List;

@Repository
public interface LegalReferenceRepository extends JpaRepository<LegalReference, Long> {

    List<LegalReference> findByEntityTypeAndEntityId(EntityType entityType, Long entityId);

    List<LegalReference> findByEntityType(EntityType entityType);
}
