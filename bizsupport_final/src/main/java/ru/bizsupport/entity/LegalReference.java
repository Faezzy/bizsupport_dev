package ru.bizsupport.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "legal_references")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LegalReference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 50)
    private EntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false)
    private String title;

    @Column(length = 500)
    private String url;

    @Column(length = 100)
    private String article;
}
