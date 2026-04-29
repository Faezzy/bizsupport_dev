package ru.bizsupport.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "company_tax_regimes",
       uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "tax_regime_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompanyTaxRegime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyProfile companyProfile;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tax_regime_id", nullable = false)
    private TaxRegime taxRegime;

    @Column(name = "is_current", nullable = false)
    @Builder.Default
    private Boolean isCurrent = true;

    @Column(name = "applied_since")
    private LocalDate appliedSince;
}
