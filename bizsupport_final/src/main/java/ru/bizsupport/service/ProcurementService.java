package ru.bizsupport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bizsupport.entity.*;
import ru.bizsupport.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProcurementService {

    private final ProcurementScenarioRepository scenarioRepo;
    private final RiskCardRepository riskCardRepo;
    private final ChecklistRepository checklistRepo;
    private final ChecklistStepRepository stepRepo;
    private final UserRepository userRepo;

    public List<ProcurementScenario> getAllScenarios() {
        return scenarioRepo.findAll();
    }

    public ProcurementScenario getScenarioById(Long id) {
        return scenarioRepo.findById(id).orElseThrow();
    }

    public List<ProcurementScenario> getMspScenarios() {
        return scenarioRepo.findByMspOnlyTrue();
    }

    public List<RiskCard> getRisksForScenario(Long scenarioId) {
        return riskCardRepo.findByScenarioId(scenarioId);
    }

    public List<Checklist> getTemplates() {
        return checklistRepo.findByIsTemplateTrue();
    }

    /**
     * Шаблоны чек-листов, релевантные сценарию: фильтр по типу закона
     * (44-ФЗ / 223-ФЗ). Так на странице сценария 223-ФЗ не показывается
     * чек-лист от 44-ФЗ и наоборот.
     */
    public List<Checklist> getTemplatesForScenario(ProcurementScenario scenario) {
        if (scenario == null || scenario.getLawType() == null) {
            return getTemplates();
        }
        List<Checklist> matched = getTemplates().stream()
                .filter(t -> t.getScenario() != null
                        && t.getScenario().getLawType() == scenario.getLawType())
                .collect(Collectors.toList());
        // Если для закона нет ни одного шаблона — показываем все, чтобы раздел не был пустым
        return matched.isEmpty() ? getTemplates() : matched;
    }

    public List<Checklist> getUserChecklists(Long userId) {
        return checklistRepo.findByUserId(userId);
    }

    public Checklist getChecklist(Long id) {
        return checklistRepo.findById(id).orElseThrow();
    }

    /** Скопировать шаблонный чек-лист для пользователя */
    @Transactional
    public Checklist copyTemplateForUser(Long templateId, Long userId) {
        Checklist template = checklistRepo.findById(templateId).orElseThrow();
        User user = userRepo.findById(userId).orElseThrow();

        Checklist copy = Checklist.builder()
                .user(user)
                .scenario(template.getScenario())
                .title(template.getTitle())
                .description(template.getDescription())
                .isTemplate(false)
                .isCompleted(false)
                .build();
        copy = checklistRepo.save(copy);

        final Checklist savedCopy = copy;
        int order = 1;
        for (ChecklistStep ts : template.getSteps()) {
            ChecklistStep step = ChecklistStep.builder()
                    .checklist(savedCopy)
                    .stepOrder(order++)
                    .title(ts.getTitle())
                    .description(ts.getDescription())
                    .hint(ts.getHint())
                    .isCompleted(false)
                    .build();
            stepRepo.save(step);
        }
        return checklistRepo.findById(savedCopy.getId()).orElseThrow();
    }

    /** Отметить шаг как выполненный / невыполненный */
    @Transactional
    public ChecklistStep toggleStep(Long stepId) {
        ChecklistStep step = stepRepo.findById(stepId).orElseThrow();
        step.setIsCompleted(!step.getIsCompleted());
        step.setCompletedAt(step.getIsCompleted() ? LocalDateTime.now() : null);
        stepRepo.save(step);

        // Обновить статус чек-листа
        Checklist cl = step.getChecklist();
        boolean allDone = cl.getSteps().stream().allMatch(ChecklistStep::getIsCompleted);
        cl.setIsCompleted(allDone);
        checklistRepo.save(cl);

        return step;
    }
}
