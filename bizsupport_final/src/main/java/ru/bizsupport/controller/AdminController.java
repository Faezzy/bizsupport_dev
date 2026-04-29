package ru.bizsupport.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.bizsupport.entity.*;
import ru.bizsupport.repository.*;
import ru.bizsupport.service.*;

import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepo;
    private final CompanyProfileRepository profileRepo;
    private final TaxRegimeRepository taxRegimeRepo;
    private final TaxObligationRepository taxObligationRepo;
    private final ProcurementScenarioRepository scenarioRepo;
    private final RiskCardRepository riskCardRepo;
    private final ChecklistRepository checklistRepo;
    private final NotificationRepository notificationRepo;
    private final DeadlineRepository deadlineRepo;

    // ── Dashboard ─────────────────────────────────────────────
    @GetMapping
    String adminDashboard(Model m) {
        m.addAttribute("userCount", userRepo.count());
        m.addAttribute("profileCount", profileRepo.count());
        m.addAttribute("regimeCount", taxRegimeRepo.count());
        m.addAttribute("scenarioCount", scenarioRepo.count());
        m.addAttribute("riskCount", riskCardRepo.count());
        m.addAttribute("checklistCount", checklistRepo.count());
        m.addAttribute("notificationCount", notificationRepo.count());
        m.addAttribute("deadlineCount", deadlineRepo.count());
        return "admin/index";
    }

    // ── Users ─────────────────────────────────────────────────
    @GetMapping("/users")
    String usersList(Model m) {
        m.addAttribute("users", userRepo.findAll());
        return "admin/users";
    }

    @PostMapping("/users/{id}/toggle-role")
    String toggleRole(@PathVariable Long id) {
        userRepo.findById(id).ifPresent(user -> {
            user.setRole(user.getRole() == User.Role.ADMIN ? User.Role.USER : User.Role.ADMIN);
            userRepo.save(user);
        });
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/delete")
    String deleteUser(@PathVariable Long id, @AuthenticationPrincipal UserDetails ud) {
        // Нельзя удалить самого себя
        userRepo.findById(id).ifPresent(user -> {
            if (!user.getEmail().equals(ud.getUsername())) {
                userRepo.delete(user);
            }
        });
        return "redirect:/admin/users";
    }

    // ── Tax Regimes ───────────────────────────────────────────
    @GetMapping("/regimes")
    String regimesList(Model m) {
        m.addAttribute("regimes", taxRegimeRepo.findAll());
        return "admin/regimes";
    }

    @GetMapping("/regimes/edit/{id}")
    String editRegime(@PathVariable Long id, Model m) {
        m.addAttribute("regime", taxRegimeRepo.findById(id).orElseThrow());
        m.addAttribute("obligations", taxObligationRepo.findByTaxRegimeId(id));
        return "admin/regime-edit";
    }

    @PostMapping("/regimes/save")
    String saveRegime(@RequestParam(required = false) Long id,
                      @RequestParam String code,
                      @RequestParam String name,
                      @RequestParam(required = false) String description,
                      @RequestParam(required = false) String conditions,
                      @RequestParam(required = false) String nkRef) {
        TaxRegime regime;
        if (id != null) {
            regime = taxRegimeRepo.findById(id).orElseThrow();
        } else {
            regime = new TaxRegime();
        }
        regime.setCode(code);
        regime.setName(name);
        regime.setDescription(description);
        regime.setConditions(conditions);
        regime.setNkRef(nkRef);
        taxRegimeRepo.save(regime);
        return "redirect:/admin/regimes";
    }

    // ── Procurement Scenarios ─────────────────────────────────
    @GetMapping("/scenarios")
    String scenariosList(Model m) {
        m.addAttribute("scenarios", scenarioRepo.findAll());
        return "admin/scenarios";
    }

    @GetMapping("/scenarios/edit/{id}")
    String editScenario(@PathVariable Long id, Model m) {
        m.addAttribute("scenario", scenarioRepo.findById(id).orElseThrow());
        m.addAttribute("risks", riskCardRepo.findByScenarioId(id));
        return "admin/scenario-edit";
    }

    @PostMapping("/scenarios/save")
    String saveScenario(@RequestParam(required = false) Long id,
                        @RequestParam String lawType,
                        @RequestParam String title,
                        @RequestParam(required = false) String description,
                        @RequestParam(required = false, defaultValue = "false") boolean mspOnly) {
        ProcurementScenario scenario;
        if (id != null) {
            scenario = scenarioRepo.findById(id).orElseThrow();
        } else {
            scenario = new ProcurementScenario();
        }
        scenario.setLawType(ProcurementScenario.LawType.valueOf(lawType));
        scenario.setTitle(title);
        scenario.setDescription(description);
        scenario.setMspOnly(mspOnly);
        scenarioRepo.save(scenario);
        return "redirect:/admin/scenarios";
    }

    // ── Risk Cards ────────────────────────────────────────────
    @GetMapping("/risks")
    String risksList(Model m) {
        m.addAttribute("risks", riskCardRepo.findAll());
        m.addAttribute("scenarios", scenarioRepo.findAll());
        return "admin/risks";
    }

    @PostMapping("/risks/save")
    String saveRisk(@RequestParam(required = false) Long id,
                    @RequestParam Long scenarioId,
                    @RequestParam String title,
                    @RequestParam String riskType,
                    @RequestParam(required = false) String description,
                    @RequestParam(required = false) String consequence,
                    @RequestParam(required = false) String recommendation) {
        RiskCard risk;
        if (id != null) {
            risk = riskCardRepo.findById(id).orElseThrow();
        } else {
            risk = new RiskCard();
        }
        risk.setScenario(scenarioRepo.findById(scenarioId).orElseThrow());
        risk.setTitle(title);
        risk.setRiskType(RiskCard.RiskType.valueOf(riskType));
        risk.setDescription(description);
        risk.setConsequence(consequence);
        risk.setRecommendation(recommendation);
        riskCardRepo.save(risk);
        return "redirect:/admin/risks";
    }

    @PostMapping("/risks/{id}/delete")
    String deleteRisk(@PathVariable Long id) {
        riskCardRepo.deleteById(id);
        return "redirect:/admin/risks";
    }
}
