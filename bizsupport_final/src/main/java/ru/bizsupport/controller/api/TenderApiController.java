package ru.bizsupport.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.bizsupport.dto.TenderDtos.*;
import ru.bizsupport.service.TenderService;

@RestController
@RequestMapping("/api/tenders")
@RequiredArgsConstructor
public class TenderApiController {

    private final TenderService tenderService;

    /** GET /api/tenders?query=...&lawType=FZ_44&page=0&size=20 */
    @GetMapping
    public TenderListResponse list(@ModelAttribute FilterRequest filter) {
        return tenderService.search(filter);
    }

    @GetMapping("/{id}")
    public TenderDetail detail(@PathVariable Long id) {
        return tenderService.getDetail(id);
    }

    @GetMapping("/stats")
    public TenderStats stats() {
        return tenderService.getStats();
    }
}
