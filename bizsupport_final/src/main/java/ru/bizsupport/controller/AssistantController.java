package ru.bizsupport.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.bizsupport.dto.assistant.AssistantMessage;
import ru.bizsupport.service.AssistantService;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/assistant")
@RequiredArgsConstructor
public class AssistantController {

    public static final String SESSION_KEY = "assistantHistory";
    public static final int MAX_HISTORY = 50; // ограничение на длину диалога в сессии

    private final AssistantService assistantService;

    @GetMapping
    public String index(Model model, HttpSession session) {
        List<AssistantMessage> history = getHistory(session);
        model.addAttribute("history", history);
        model.addAttribute("isConfigured", assistantService.isConfigured());
        model.addAttribute("model", assistantService.getModel());
        return "assistant/index";
    }

    @PostMapping("/clear")
    public String clear(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
        return "redirect:/assistant";
    }

    @SuppressWarnings("unchecked")
    public static List<AssistantMessage> getHistory(HttpSession session) {
        List<AssistantMessage> history = (List<AssistantMessage>) session.getAttribute(SESSION_KEY);
        return history != null ? history : new ArrayList<>();
    }

    public static void saveHistory(HttpSession session, List<AssistantMessage> history) {
        // Усечение, чтобы сессия не разрасталась
        if (history.size() > MAX_HISTORY) {
            history = new ArrayList<>(history.subList(history.size() - MAX_HISTORY, history.size()));
        }
        session.setAttribute(SESSION_KEY, history);
    }
}
