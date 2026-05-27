package ru.bizsupport.dto.assistant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class AssistantDtos {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChatRequest {
        private String message;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChatResponse {
        private String reply;
        private LocalDateTime timestamp;
        private boolean error;

        public static ChatResponse ok(String reply) {
            return new ChatResponse(reply, LocalDateTime.now(), false);
        }

        public static ChatResponse error(String message) {
            return new ChatResponse(message, LocalDateTime.now(), true);
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HistoryResponse {
        private List<AssistantMessage> messages;
        private int count;
    }
}
