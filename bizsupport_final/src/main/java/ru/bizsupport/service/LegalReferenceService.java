package ru.bizsupport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.bizsupport.entity.EntityType;
import ru.bizsupport.entity.LegalReference;
import ru.bizsupport.repository.LegalReferenceRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LegalReferenceService {

    private final LegalReferenceRepository legalRefRepo;

    /** Правовые ссылки для конкретной сущности */
    public List<LegalReference> getReferences(EntityType type, Long entityId) {
        return legalRefRepo.findByEntityTypeAndEntityId(type, entityId);
    }

    /** Все ссылки по типу */
    public List<LegalReference> getAllByType(EntityType type) {
        return legalRefRepo.findByEntityType(type);
    }
}
