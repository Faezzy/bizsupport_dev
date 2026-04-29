package ru.bizsupport.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tax_regimes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaxRegime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;   // USN_6, USN_15, OSNO, PSN, NPD

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // JSON-строка с условиями: max_revenue, max_employees, allowed_types
    @Column(columnDefinition = "TEXT")
    private String conditions;

    @Column(name = "nk_ref", length = 255)
    private String nkRef;   // ссылка на статью НК РФ

    @OneToMany(mappedBy = "taxRegime", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TaxObligation> taxObligations = new ArrayList<>();

    @OneToMany(mappedBy = "taxRegime", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Deadline> deadlines = new ArrayList<>();

    @OneToMany(mappedBy = "taxRegime", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CompanyTaxRegime> companyTaxRegimes = new ArrayList<>();
}
