package ru.bizsupport.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "procurement_scenarios")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcurementScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "law_type", nullable = false, length = 10)
    private LawType lawType;  // FZ_44 / FZ_223

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "msp_only", nullable = false)
    @Builder.Default
    private Boolean mspOnly = false;

    @Column(name = "amount_min", precision = 15, scale = 2)
    private BigDecimal amountMin;

    @Column(name = "amount_max", precision = 15, scale = 2)
    private BigDecimal amountMax;

    @OneToMany(mappedBy = "scenario", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RiskCard> riskCards = new ArrayList<>();

    public enum LawType {
        FZ_44("44-ФЗ"),
        FZ_223("223-ФЗ");

        private final String displayName;
        LawType(String d) { this.displayName = d; }
        public String getDisplayName() { return displayName; }
    }
}
