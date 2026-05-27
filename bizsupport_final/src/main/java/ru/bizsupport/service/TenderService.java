package ru.bizsupport.service;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import ru.bizsupport.dto.TenderDtos.*;
import ru.bizsupport.entity.Tender;
import ru.bizsupport.entity.TenderLawType;
import ru.bizsupport.entity.TenderStatus;
import ru.bizsupport.repository.TenderRepository;
import ru.bizsupport.service.provider.TenderProvider;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenderService {

    private final TenderRepository repo;
    private final List<TenderProvider> providers;

    @Value("${app.tenders.provider:mock}")
    private String activeProvider;

    @Value("${app.tenders.auto-load:true}")
    private boolean autoLoad;

    @PostConstruct
    public void init() {
        if (!autoLoad) return;
        if (repo.count() > 0) {
            log.info("Tenders already loaded ({} records). Skipping initial fetch.", repo.count());
            return;
        }
        TenderProvider provider = providers.stream()
                .filter(p -> p.getName().equals(activeProvider))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Provider not found: " + activeProvider));

        log.info("Loading initial tenders from provider [{}]...", provider.getName());
        List<Tender> initial = provider.fetchInitial();
        repo.saveAll(initial);
        log.info("Loaded {} tenders.", initial.size());
    }

    /** Список с фильтрами и пагинацией */
    public TenderListResponse search(FilterRequest filter) {
        Sort sort = parseSort(filter.getSort());
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);
        Specification<Tender> spec = buildSpec(filter);
        Page<Tender> page = repo.findAll(spec, pageable);

        List<TenderCard> cards = page.getContent().stream()
                .map(this::toCard).collect(Collectors.toList());

        // Собираем доступные регионы и категории — для UI-фильтров
        List<String> regions = repo.findAll().stream()
                .map(Tender::getRegion).filter(Objects::nonNull).distinct().sorted().toList();
        List<String> categories = repo.findAll().stream()
                .map(Tender::getCategory).filter(Objects::nonNull).distinct().sorted().toList();

        return TenderListResponse.builder()
                .items(cards)
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(page.getNumber())
                .availableRegions(regions)
                .availableCategories(categories)
                .build();
    }

    public TenderDetail getDetail(Long id) {
        Tender t = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Tender not found: " + id));
        return toDetail(t);
    }

    public TenderStats getStats() {
        return TenderStats.builder()
                .total(repo.count())
                .published(repo.countByStatus(TenderStatus.PUBLISHED))
                .underReview(repo.countByStatus(TenderStatus.UNDER_REVIEW))
                .completed(repo.countByStatus(TenderStatus.COMPLETED))
                .build();
    }

    private Specification<Tender> buildSpec(FilterRequest f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (f.getQuery() != null && !f.getQuery().isBlank()) {
                String pattern = "%" + f.getQuery().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("customerName")), pattern),
                        cb.like(cb.lower(root.get("registryNumber")), pattern)
                ));
            }
            if (f.getLawType() != null) predicates.add(cb.equal(root.get("lawType"), f.getLawType()));
            if (f.getStatus() != null) predicates.add(cb.equal(root.get("status"), f.getStatus()));
            if (f.getRegion() != null && !f.getRegion().isBlank())
                predicates.add(cb.equal(root.get("region"), f.getRegion()));
            if (f.getCategory() != null && !f.getCategory().isBlank())
                predicates.add(cb.equal(root.get("category"), f.getCategory()));
            if (f.getPriceFrom() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("initialPrice"), f.getPriceFrom()));
            if (f.getPriceTo() != null) predicates.add(cb.lessThanOrEqualTo(root.get("initialPrice"), f.getPriceTo()));
            if (Boolean.TRUE.equals(f.getMspOnly())) predicates.add(cb.isTrue(root.get("mspOnly")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Sort parseSort(String s) {
        if (s == null || s.isBlank()) return Sort.by(Sort.Direction.DESC, "publishedAt");
        String[] parts = s.split(",");
        Sort.Direction dir = parts.length > 1 && "asc".equalsIgnoreCase(parts[1]) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, parts[0]);
    }

    private TenderCard toCard(Tender t) {
        return TenderCard.builder()
                .id(t.getId())
                .registryNumber(t.getRegistryNumber())
                .title(t.getTitle())
                .customerName(t.getCustomerName())
                .lawType(t.getLawType().name())
                .lawTypeDisplay(t.getLawType().getDisplayName())
                .initialPrice(t.getInitialPrice())
                .region(t.getRegion())
                .category(t.getCategory())
                .submissionDeadline(t.getSubmissionDeadline())
                .status(t.getStatus().name())
                .statusDisplay(t.getStatus().getDisplayName())
                .statusBadge(t.getStatus().getBadgeStyle())
                .mspOnly(t.getMspOnly())
                .daysToDeadline(daysTo(t.getSubmissionDeadline()))
                .build();
    }

    private TenderDetail toDetail(Tender t) {
        return TenderDetail.builder()
                .id(t.getId())
                .registryNumber(t.getRegistryNumber())
                .title(t.getTitle())
                .description(t.getDescription())
                .customerName(t.getCustomerName())
                .customerInn(t.getCustomerInn())
                .lawType(t.getLawType().name())
                .lawTypeDisplay(t.getLawType().getDisplayName())
                .procurementMethod(t.getProcurementMethod())
                .initialPrice(t.getInitialPrice())
                .currency(t.getCurrency())
                .region(t.getRegion())
                .okpdCode(t.getOkpdCode())
                .category(t.getCategory())
                .publishedAt(t.getPublishedAt())
                .submissionDeadline(t.getSubmissionDeadline())
                .auctionDate(t.getAuctionDate())
                .applicationSecurity(t.getApplicationSecurity())
                .contractSecurity(t.getContractSecurity())
                .mspOnly(t.getMspOnly())
                .status(t.getStatus().name())
                .statusDisplay(t.getStatus().getDisplayName())
                .statusBadge(t.getStatus().getBadgeStyle())
                .sourceUrl(t.getSourceUrl())
                .source(t.getSource())
                .daysToDeadline(daysTo(t.getSubmissionDeadline()))
                .build();
    }

    private long daysTo(LocalDateTime deadline) {
        if (deadline == null) return 0;
        return ChronoUnit.DAYS.between(LocalDateTime.now(), deadline);
    }
}
