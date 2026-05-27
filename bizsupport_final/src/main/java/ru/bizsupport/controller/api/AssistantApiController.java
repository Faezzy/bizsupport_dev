package ru.bizsupport.controller.api;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.bizsupport.controller.AssistantController;
import ru.bizsupport.dto.assistant.AssistantDtos.ChatRequest;
import ru.bizsupport.dto.assistant.AssistantDtos.ChatResponse;
import ru.bizsupport.dto.assistant.AssistantDtos.HistoryResponse;
import ru.bizsupport.dto.assistant.AssistantMessage;
import ru.bizsupport.service.AssistantService;

import java.util.List;
import java.util.Map;

/**
 * REST API ассистента — используется как из веб-страницы (AJAX),
 * так и из мобильных/десктопных KMP-клиентов.
 *
 * Endpoints:
 *   POST   /api/assistant/chat     — отправить сообщение
 *   GET    /api/assistant/history  — получить историю диалога
 *   DELETE /api/assistant/history  — очистить историю
 */
@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantApiController {

    private final AssistantService assistantService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request, HttpSession session) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest().body(ChatResponse.error("Сообщение пустое"));
        }

        List<AssistantMessage> history = AssistantController.getHistory(session);

        // Передаём в LLM последние 10 сообщений для контекста (без системного промпта)
        int contextSize = Math.min(history.size(), 10);
        List<AssistantMessage> context = history.subList(history.size() - contextSize, history.size());

        String reply = assistantService.chat(context, request.getMessage());

        history.add(AssistantMessage.user(request.getMessage()));
        history.add(AssistantMessage.assistant(reply));
        AssistantController.saveHistory(session, history);

        return ResponseEntity.ok(ChatResponse.ok(reply));
    }

    @GetMapping("/history")
    public ResponseEntity<HistoryResponse> history(HttpSession session) {
        List<AssistantMessage> history = AssistantController.getHistory(session);
        return ResponseEntity.ok(new HistoryResponse(history, history.size()));
    }

    @DeleteMapping("/history")
    public ResponseEntity<Map<String, String>> clearHistory(HttpSession session) {
        session.removeAttribute(AssistantController.SESSION_KEY);
        return ResponseEntity.ok(Map.of("status", "cleared"));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "configured", assistantService.isConfigured(),
                "model", assistantService.getModel()
        ));
    }
}
