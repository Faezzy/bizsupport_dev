package ru.bizsupport.controller.api;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.bizsupport.service.TenderAnalysisService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST-эндпоинты вспомогательного модуля AI-анализа тендеров.
 *
 * POST /api/tenders/{id}/analyze       — анализ одного тендера (с кэшем)
 * POST /api/tenders/analyze-list       — сводный анализ списка тендеров
 * POST /api/tenders/match              — AI-матчер: подобрать тендеры под профиль
 * POST /api/tenders/match-feed         — AI-матчер по текущей ленте
 */
@RestController
@RequestMapping("/api/tenders")
@RequiredArgsConstructor
public class TenderAnalysisApiController {

    private final TenderAnalysisService analysisService;

    @PostMapping("/{id}/analyze")
    public ResponseEntity<AnalysisResponse> analyzeSingle(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean refresh) {
        try {
            String result = analysisService.analyzeTender(id, refresh);
            return ResponseEntity.ok(reply(result));
        } catch (Exception e) {
            return ResponseEntity.ok(new AnalysisResponse("Ошибка анализа: " + e.getMessage(), null, true));
        }
    }

    @PostMapping("/analyze-list")
    public ResponseEntity<AnalysisResponse> analyzeList(@RequestBody ListAnalysisRequest req) {
        if (req.getIds() == null || req.getIds().isEmpty()) {
            return ResponseEntity.badRequest().body(new AnalysisResponse("Список ID тендеров пуст", null, true));
        }
        try {
            String result = analysisService.analyzeList(req.getIds());
            return ResponseEntity.ok(reply(result));
        } catch (Exception e) {
            return ResponseEntity.ok(new AnalysisResponse("Ошибка анализа: " + e.getMessage(), null, true));
        }
    }

    @PostMapping("/match")
    public ResponseEntity<AnalysisResponse> match(@RequestBody MatchRequest req) {
        if (req.getCompanyContext() == null || req.getCompanyContext().isBlank()) {
            return ResponseEntity.badRequest().body(new AnalysisResponse("Укажите описание компании/профиль", null, true));
        }
        try {
            List<Long> ids = req.getIds() != null ? req.getIds() : List.of();
            String result = ids.isEmpty()
                    ? analysisService.matchCurrentFeed(req.getCompanyContext())
                    : analysisService.matchTenders(req.getCompanyContext(), ids);
            return ResponseEntity.ok(reply(result));
        } catch (Exception e) {
            return ResponseEntity.ok(new AnalysisResponse("Ошибка матчинга: " + e.getMessage(), null, true));
        }
    }

    /** Оборачивает ответ ассистента, помечая служебные сообщения об ошибке. */
    private AnalysisResponse reply(String result) {
        boolean isError = TenderAnalysisService.isErrorResponse(result);
        return new AnalysisResponse(result, analysisService.getAssistantModel(), isError);
    }

    // ─── DTOs ───────────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    public static class ListAnalysisRequest {
        private List<Long> ids;
    }

    @Data
    @NoArgsConstructor
    public static class MatchRequest {
        private String companyContext;
        private List<Long> ids;
    }

    @lombok.AllArgsConstructor
    @Data
    public static class AnalysisResponse {
        private String result;
        private String model;
        private boolean error;
        private LocalDateTime timestamp = LocalDateTime.now();

        public AnalysisResponse(String result, String model, boolean error) {
            this.result = result;
            this.model = model;
            this.error = error;
        }
    }

}
