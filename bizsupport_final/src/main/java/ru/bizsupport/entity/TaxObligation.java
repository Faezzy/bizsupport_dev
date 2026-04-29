package ru.bizsupport.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tax_obligations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaxObligation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_regime_id", nullable = false)
    private TaxRegime taxRegime;

    @Column(name = "tax_name", nullable = false)
    private String taxName;

    @Column(length = 50)
    private String rate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "nk_ref", length = 255)
    private String nkRef;

    @Column(name = "fns_service_url", length = 500)
    private String fnsServiceUrl;
}
