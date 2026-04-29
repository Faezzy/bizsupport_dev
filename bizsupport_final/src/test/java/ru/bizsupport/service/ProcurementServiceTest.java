package ru.bizsupport.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.bizsupport.entity.*;
import ru.bizsupport.repository.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcurementService — юнит-тесты")
class ProcurementServiceTest {

    @Mock private ProcurementScenarioRepository scenarioRepo;
    @Mock private RiskCardRepository riskCardRepo;
    @Mock private ChecklistRepository checklistRepo;
    @Mock private ChecklistStepRepository stepRepo;
    @Mock private UserRepository userRepo;

    @InjectMocks private ProcurementService procurementService;

    @Test
    @DisplayName("getAllScenarios — возвращает все сценарии")
    void getAllScenarios() {
        when(scenarioRepo.findAll()).thenReturn(List.of(
                ProcurementScenario.builder().id(1L).title("44-ФЗ").build(),
                ProcurementScenario.builder().id(2L).title("223-ФЗ").build()
        ));
        assertThat(procurementService.getAllScenarios()).hasSize(2);
    }

    @Test
    @DisplayName("getMspScenarios — возвращает только МСП")
    void getMspScenarios() {
        ProcurementScenario msp = ProcurementScenario.builder().id(2L).mspOnly(true).build();
        when(scenarioRepo.findByMspOnlyTrue()).thenReturn(List.of(msp));
        List<ProcurementScenario> result = procurementService.getMspScenarios();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMspOnly()).isTrue();
    }

    @Test
    @DisplayName("getRisksForScenario — делегирует в репозиторий")
    void getRisksForScenario() {
        RiskCard risk = RiskCard.builder().id(1L).title("Риск").riskType(RiskCard.RiskType.FINANCIAL).build();
        when(riskCardRepo.findByScenarioId(1L)).thenReturn(List.of(risk));
        assertThat(procurementService.getRisksForScenario(1L)).hasSize(1);
    }

    @Test
    @DisplayName("toggleStep — переключает статус шага")
    void toggleStep() {
        Checklist checklist = Checklist.builder().id(1L).isCompleted(false)
                .steps(new ArrayList<>()).build();
        ChecklistStep step = ChecklistStep.builder()
                .id(1L).isCompleted(false).checklist(checklist).build();
        checklist.getSteps().add(step);

        when(stepRepo.findById(1L)).thenReturn(Optional.of(step));
        when(stepRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(checklistRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChecklistStep result = procurementService.toggleStep(1L);
        assertThat(result.getIsCompleted()).isTrue();
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("toggleStep — повторный toggle снимает отметку")
    void toggleStepBack() {
        Checklist checklist = Checklist.builder().id(1L).isCompleted(false)
                .steps(new ArrayList<>()).build();
        ChecklistStep step = ChecklistStep.builder()
                .id(1L).isCompleted(true).checklist(checklist).build();
        checklist.getSteps().add(step);

        when(stepRepo.findById(1L)).thenReturn(Optional.of(step));
        when(stepRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(checklistRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChecklistStep result = procurementService.toggleStep(1L);
        assertThat(result.getIsCompleted()).isFalse();
        assertThat(result.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("getTemplates — возвращает только шаблоны")
    void getTemplates() {
        when(checklistRepo.findByIsTemplateTrue()).thenReturn(List.of(
                Checklist.builder().id(1L).isTemplate(true).title("Шаблон").build()
        ));
        assertThat(procurementService.getTemplates()).hasSize(1);
    }
}
