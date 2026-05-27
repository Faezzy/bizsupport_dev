package ru.bizsupport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.bizsupport.dto.assistant.AssistantMessage;

import java.util.*;

/**
 * Универсальный сервис AI-ассистента.
 * Совместим с любым OpenAI-style API (endpoint /chat/completions):
 *   - OpenAI:        https://api.openai.com/v1
 *   - OpenRouter:    https://openrouter.ai/api/v1
 *   - DeepSeek:      https://api.deepseek.com/v1
 *   - Ollama:        http://localhost:11434/v1
 *   - LM Studio:     http://localhost:1234/v1
 *   - GigaChat-proxy / любой self-hosted llama.cpp server
 *
 * Настройки в application.properties (см. assistant.properties.snippet)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssistantService {

    @Value("${app.assistant.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${app.assistant.api-key:}")
    private String apiKey;

    @Value("${app.assistant.model:gpt-4o-mini}")
    private String model;

    @Value("${app.assistant.max-tokens:1000}")
    private Integer maxTokens;

    @Value("${app.assistant.temperature:0.7}")
    private Double temperature;

    @Value("${app.assistant.timeout-ms:30000}")
    private Integer timeoutMs;

    private static final String SYSTEM_PROMPT = """
            Ты — AI-ассистент BizSupport, помогаешь предпринимателям РФ ориентироваться в налогах и госзакупках.

            Темы, в которых ты разбираешься:
            • Налоговые режимы: УСН 6%, УСН 15%, ОСНО, ПСН, НПД (самозанятость)
            • Страховые взносы (фиксированные ИП, НДФЛ, взносы за сотрудников)
            • Госзакупки по 44-ФЗ и 223-ФЗ (закупки для МСП, обеспечение, РНП)
            • Дедлайны деклараций, отчётности, оплаты налогов
            • Документооборот: УКЭП, ЕИС, торговые площадки (Сбер-АСТ, РТС, ТЭК-Торг)
            • Выбор оптимального налогового режима под параметры бизнеса

            Стиль ответа:
            • Кратко и по делу, на русском языке
            • При необходимости ссылайся на конкретные статьи НК РФ или закона (например, "ст. 346.20 НК РФ")
            • Если вопрос сложный или индивидуальный — добавляй: "В сложных случаях рекомендую консультацию налогового специалиста."
            • Не давай советов вне темы налогов и предпринимательства
            • Если данных недостаточно — задай уточняющий вопрос
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Отправить сообщение в LLM с учётом истории диалога.
     */
    public String chat(List<AssistantMessage> history, String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AI assistant called but app.assistant.api-key is not set");
            return "⚙️ AI-ассистент пока не настроен.\n\n" +
                   "Администратору: укажите `app.assistant.base-url` и `app.assistant.api-key` " +
                   "в application.properties — и ассистент заработает с любым OpenAI-совместимым API.";
        }

        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));

            if (history != null) {
                for (AssistantMessage m : history) {
                    messages.add(Map.of("role", m.getRole(), "content", m.getContent()));
                }
            }
            messages.add(Map.of("role", "user", "content", userMessage));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("max_tokens", maxTokens);
            body.put("temperature", temperature);
            body.put("stream", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            RestTemplate rt = new RestTemplate();
            rt.getMessageConverters().forEach(c -> {
                if (c instanceof org.springframework.http.converter.StringHttpMessageConverter shmc) {
                    shmc.setDefaultCharset(java.nio.charset.StandardCharsets.UTF_8);
                }
            });

            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            String url = trimSlash(baseUrl) + "/chat/completions";

            log.debug("AI request to {} with model={}", url, model);
            ResponseEntity<Map> resp = rt.postForEntity(url, req, Map.class);

            return extractContent(resp.getBody());

        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("AI API error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return "⚠️ Ошибка API (" + e.getStatusCode().value() + "). " +
                   "Проверьте api-key, model и base-url в настройках.";
        } catch (Exception e) {
            log.error("AI assistant error", e);
            return "⚠️ Ошибка обращения к ассистенту: " + e.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> body) {
        if (body == null) return "Пустой ответ от модели.";
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
            if (choices == null || choices.isEmpty()) return "Модель не вернула вариант ответа.";
            Map<String, Object> firstChoice = choices.get(0);
            Map<String, String> message = (Map<String, String>) firstChoice.get("message");
            return message != null ? message.get("content") : "Ответ без содержимого.";
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", body, e);
            return "Не удалось распарсить ответ модели.";
        }
    }

    private String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getModel() {
        return model;
    }
}
