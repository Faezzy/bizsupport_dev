package ru.bizsupport.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "saved_searches")
@JsonIgnoreProperties({"user", "hibernateLazyInitializer", "handler"})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SavedSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String query;

    @Column(name = "law_type", length = 10)
    private String lawType;

    @Column(name = "price_from", precision = 15, scale = 2)
    private BigDecimal priceFrom;

    @Column(name = "price_to", precision = 15, scale = 2)
    private BigDecimal priceTo;

    @Column(length = 200)
    private String region;

    @Column(name = "msp_only", nullable = false)
    @Builder.Default
    private Boolean mspOnly = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Column(name = "last_result_count", nullable = false)
    @Builder.Default
    private int lastResultCount = -1;
}
