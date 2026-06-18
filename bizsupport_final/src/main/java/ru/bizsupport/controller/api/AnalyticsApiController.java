package ru.bizsupport.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.bizsupport.dto.TenderDtos.TenderAnalytics;
import ru.bizsupport.service.TenderService;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsApiController {

    private final TenderService tenderService;

    @GetMapping("/tenders")
    public ResponseEntity<TenderAnalytics> tenderAnalytics() {
        return ResponseEntity.ok(tenderService.getAnalytics());
    }
}
