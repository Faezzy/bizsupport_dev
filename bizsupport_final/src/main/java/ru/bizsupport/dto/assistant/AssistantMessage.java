package ru.bizsupport.dto.assistant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Сообщение в диалоге с AI-ассистентом.
 * role: "user" или "assistant"
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AssistantMessage implements Serializable {
    private String role;
    private String content;
    private LocalDateTime timestamp;

    public static AssistantMessage user(String content) {
        return new AssistantMessage("user", content, LocalDateTime.now());
    }

    public static AssistantMessage assistant(String content) {
        return new AssistantMessage("assistant", content, LocalDateTime.now());
    }
}
