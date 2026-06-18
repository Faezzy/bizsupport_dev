package ru.bizsupport.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import ru.bizsupport.entity.Tender;
import ru.bizsupport.entity.TenderStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenderRepository extends JpaRepository<Tender, Long>, JpaSpecificationExecutor<Tender> {

    Optional<Tender> findByRegistryNumber(String registryNumber);

    List<Tender> findTop10ByStatusOrderByPublishedAtDesc(TenderStatus status);

    Page<Tender> findByStatus(TenderStatus status, Pageable pageable);

    /** Ближайшие по дедлайну открытые тендеры (для виджета на дашборде) */
    List<Tender> findByStatusAndSubmissionDeadlineAfterOrderBySubmissionDeadlineAsc(
            TenderStatus status, LocalDateTime after, Pageable pageable);

    long countByStatus(TenderStatus status);
}
