package ru.bizsupport.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "risk_cards")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RiskCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_id")
    private ProcurementScenario scenario;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_type", length = 20)
    private RiskType riskType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String consequence;

    @Column(columnDefinition = "TEXT")
    private String recommendation;

    public enum RiskType {
        FINANCIAL("Финансовый"),
        LEGAL("Юридический"),
        PROCEDURAL("Процедурный");

        private final String displayName;
        RiskType(String d) { this.displayName = d; }
        public String getDisplayName() { return displayName; }
    }
}
