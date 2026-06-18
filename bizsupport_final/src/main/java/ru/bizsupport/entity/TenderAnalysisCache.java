package ru.bizsupport.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tender_analysis_cache", indexes = {
        @Index(name = "idx_analysis_tender", columnList = "tender_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenderAnalysisCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tender_id", nullable = false)
    private Tender tender;

    /** Модель, которая выполнила анализ */
    @Column(nullable = false, length = 100)
    private String model;

    /** JSON с результатами анализа */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String analysis;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
