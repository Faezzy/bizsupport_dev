package ru.bizsupport.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenders", indexes = {
        @Index(name = "idx_tender_law", columnList = "law_type"),
        @Index(name = "idx_tender_status", columnList = "status"),
        @Index(name = "idx_tender_region", columnList = "region"),
        @Index(name = "idx_tender_deadline", columnList = "submission_deadline")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tender {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Реестровый номер закупки (как в ЕИС: 0173100012324000456) */
    @Column(name = "registry_number", nullable = false, unique = true, length = 30)
    private String registryNumber;

    /** Название закупки */
    @Column(nullable = false, length = 1000)
    private String title;

    /** Полное описание предмета закупки */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Заказчик */
    @Column(name = "customer_name", nullable = false, length = 500)
    private String customerName;

    /** ИНН заказчика */
    @Column(name = "customer_inn", length = 12)
    private String customerInn;

    /** Закон, по которому проводится закупка */
    @Enumerated(EnumType.STRING)
    @Column(name = "law_type", nullable = false, length = 20)
    private TenderLawType lawType;

    /** Способ закупки: электронный аукцион, запрос котировок, ... */
    @Column(name = "procurement_method", length = 100)
    private String procurementMethod;

    /** Начальная (максимальная) цена контракта */
    @Column(name = "initial_price", precision = 15, scale = 2)
    private BigDecimal initialPrice;

    @Column(length = 3)
    private String currency;

    /** Регион */
    @Column(length = 200)
    private String region;

    /** ОКПД2 код */
    @Column(name = "okpd_code", length = 20)
    private String okpdCode;

    /** Категория (укрупнённо: строительство, медицина, IT, ...) */
    @Column(length = 100)
    private String category;

    /** Дата публикации */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** Дедлайн подачи заявок */
    @Column(name = "submission_deadline")
    private LocalDateTime submissionDeadline;

    /** Дата проведения торгов / рассмотрения */
    @Column(name = "auction_date")
    private LocalDate auctionDate;

    /** Размер обеспечения заявки (₽) */
    @Column(name = "application_security", precision = 15, scale = 2)
    private BigDecimal applicationSecurity;

    /** Размер обеспечения исполнения контракта (₽) */
    @Column(name = "contract_security", precision = 15, scale = 2)
    private BigDecimal contractSecurity;

    /** Только для МСП (квота 25% по 44-ФЗ) */
    @Column(name = "msp_only")
    private Boolean mspOnly;

    /** Статус */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TenderStatus status;

    /** Ссылка на оригинал в ЕИС */
    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    /** Источник данных (eis, mock, kontur, ...) */
    @Column(length = 50)
    private String source;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
