package ru.bizsupport.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.bizsupport.dto.TenderDtos.FilterRequest;
import ru.bizsupport.dto.TenderDtos.TenderListResponse;
import ru.bizsupport.entity.TenderLawType;
import ru.bizsupport.entity.TenderStatus;
import ru.bizsupport.service.TenderService;

import java.math.BigDecimal;

@Controller
@RequestMapping("/tenders")
@RequiredArgsConstructor
public class TenderController {

    private final TenderService tenderService;

    @GetMapping
    public String list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) TenderLawType lawType,
            @RequestParam(required = false) TenderStatus status,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal priceFrom,
            @RequestParam(required = false) BigDecimal priceTo,
            @RequestParam(required = false) Boolean mspOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "publishedAt,desc") String sort,
            Model model
    ) {
        FilterRequest filter = new FilterRequest();
        filter.setQuery(query);
        filter.setLawType(lawType);
        filter.setStatus(status);
        filter.setRegion(region);
        filter.setCategory(category);
        filter.setPriceFrom(priceFrom);
        filter.setPriceTo(priceTo);
        filter.setMspOnly(mspOnly);
        filter.setPage(page);
        filter.setSize(size);
        filter.setSort(sort);

        TenderListResponse response = tenderService.search(filter);
        model.addAttribute("response", response);
        model.addAttribute("filter", filter);
        model.addAttribute("stats", tenderService.getStats());
        model.addAttribute("lawTypes", TenderLawType.values());
        model.addAttribute("statuses", TenderStatus.values());
        return "tenders/index";
    }

    @GetMapping("/{id}")
    public String detail(@org.springframework.web.bind.annotation.PathVariable Long id, Model model) {
        model.addAttribute("tender", tenderService.getDetail(id));
        return "tenders/detail";
    }
}
