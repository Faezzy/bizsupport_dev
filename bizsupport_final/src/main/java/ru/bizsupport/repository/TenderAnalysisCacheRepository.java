package ru.bizsupport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.bizsupport.entity.TenderAnalysisCache;

import java.util.Optional;

public interface TenderAnalysisCacheRepository extends JpaRepository<TenderAnalysisCache, Long> {

    Optional<TenderAnalysisCache> findTopByTenderIdOrderByCreatedAtDesc(Long tenderId);

    void deleteByTenderId(Long tenderId);
}
