#pragma once
#include "repositories.hpp"
#include <optional>
#include <string>
#include <vector>
#include <chrono>
#include <ctime>
#include <stdexcept>

class ProcurementService {
public:
    ProcurementService(Repositories& repos) : repos_(repos) {}

    // Get all procurement scenarios
    std::vector<ProcurementScenario> get_all_scenarios() {
        return repos_.find_all_scenarios();
    }

    // Get a specific scenario by ID
    std::optional<ProcurementScenario> get_scenario_by_id(int64_t id) {
        return repos_.find_scenario_by_id(id);
    }

    // Get MSP-only scenarios
    std::vector<ProcurementScenario> get_msp_scenarios() {
        return repos_.find_msp_scenarios();
    }

    // Get risk cards for a scenario
    std::vector<RiskCard> get_risks_for_scenario(int64_t scenario_id) {
        return repos_.find_risks_by_scenario(scenario_id);
    }

    // Get template checklists
    std::vector<Checklist> get_templates() {
        return repos_.find_template_checklists();
    }

    // Get checklists for a specific user
    std::vector<Checklist> get_user_checklists(int64_t user_id) {
        return repos_.find_user_checklists(user_id);
    }

    // Get a specific checklist by ID (with steps)
    std::optional<Checklist> get_checklist(int64_t id) {
        return repos_.find_checklist_by_id(id);
    }

    // Deep-copy a template checklist for a specific user
    Checklist copy_template_for_user(int64_t template_id, int64_t user_id) {
        auto tmpl_opt = repos_.find_checklist_by_id(template_id);
        if (!tmpl_opt) {
            throw std::runtime_error("Template not found: " + std::to_string(template_id));
        }
        auto& tmpl = *tmpl_opt;

        // Create a new checklist based on the template, copying all steps
        Checklist copy;
        copy.user_id = user_id;
        copy.scenario_id = tmpl.scenario_id;
        copy.title = tmpl.title;
        copy.description = tmpl.description;
        copy.is_template = false;
        copy.is_completed = false;

        int order = 1;
        for (const auto& ts : tmpl.steps) {
            ChecklistStep step;
            step.step_order = order++;
            step.title = ts.title;
            step.description = ts.description;
            step.hint = ts.hint;
            step.is_completed = false;
            step.completed_at = std::nullopt;
            copy.steps.push_back(step);
        }

        // save_checklist persists the checklist and all its steps in one transaction
        return repos_.save_checklist(copy);
    }

    // Toggle a checklist step's completion status
    ChecklistStep toggle_step(int64_t step_id) {
        auto step_opt = repos_.find_step_by_id(step_id);
        if (!step_opt) {
            throw std::runtime_error("Step not found: " + std::to_string(step_id));
        }
        auto& step = *step_opt;

        // Toggle completion and persist
        step.is_completed = !step.is_completed;
        repos_.toggle_step(step.id, step.is_completed);

        // Update parent checklist completion status
        auto checklist_opt = repos_.find_checklist_by_id(step.checklist_id);
        if (checklist_opt) {
            bool all_done = !checklist_opt->steps.empty();
            for (const auto& s : checklist_opt->steps) {
                if (s.id != step.id && !s.is_completed) {
                    all_done = false;
                    break;
                }
            }
            // account for the just-toggled step
            if (!step.is_completed) all_done = false;
            repos_.update_checklist_completion(checklist_opt->id, all_done);
        }

        // Reload step to get accurate completed_at
        auto reloaded = repos_.find_step_by_id(step_id);
        return reloaded.value_or(step);
    }

private:
    Repositories& repos_;
};
