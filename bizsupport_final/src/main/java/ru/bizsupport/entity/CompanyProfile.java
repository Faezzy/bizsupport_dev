package ru.bizsupport.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "company_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompanyProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_type", nullable = false, length = 10)
    private CompanyType companyType;  // IP / OOO

    @Column(length = 12)
    private String inn;

    @Column(length = 15)
    private String ogrn;

    @Column(length = 255)
    private String industry;

    @Column(name = "employees_count")
    private Integer employeesCount;

    @Column(name = "annual_revenue", precision = 15, scale = 2)
    private BigDecimal annualRevenue;

    @Column(name = "is_msp", nullable = false)
    @Builder.Default
    private Boolean isMsp = false;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Текущие налоговые режимы
    @OneToMany(mappedBy = "companyProfile", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CompanyTaxRegime> companyTaxRegimes = new ArrayList<>();

    // Персональные дедлайны
    @OneToMany(mappedBy = "companyProfile", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Deadline> deadlines = new ArrayList<>();

    public enum CompanyType {
        IP("Индивидуальный предприниматель"),
        OOO("Общество с ограниченной ответственностью");

        private final String displayName;
        CompanyType(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }
}
