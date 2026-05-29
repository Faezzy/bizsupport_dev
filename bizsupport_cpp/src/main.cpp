#define CPPHTTPLIB_OPENSSL_SUPPORT
#include <httplib.h>
#include <nlohmann/json.hpp>
#include <iostream>
#include <string>
#include <map>
#include <mutex>
#include <thread>
#include <chrono>
#include <csignal>
#include <ctime>
#include <iomanip>
#include <sstream>

#include "config.hpp"
#include "database.hpp"
#include "models.hpp"
#include "json_utils.hpp"
#include "jwt_utils.hpp"
#include "password_utils.hpp"
#include "repositories.hpp"
#include "auth_service.hpp"
#include "company_profile_service.hpp"
#include "tax_service.hpp"
#include "tax_calculator_service.hpp"
#include "tender_service.hpp"
#include "procurement_service.hpp"
#include "notification_service.hpp"
#include "favorite_service.hpp"
#include "legal_reference_service.hpp"
#include "assistant_service.hpp"
#include "mock_tender_provider.hpp"

using json = nlohmann::json;

// ============================================================================
// Helper: get current ISO timestamp
// ============================================================================

static std::string current_iso_time() {
    auto now = std::chrono::system_clock::now();
    auto time_t_now = std::chrono::system_clock::to_time_t(now);
    std::tm tm_now{};
    gmtime_r(&time_t_now, &tm_now);
    std::ostringstream oss;
    oss << std::put_time(&tm_now, "%Y-%m-%dT%H:%M:%SZ");
    return oss.str();
}

// ============================================================================
// Helper: extract JWT token from request
// ============================================================================

static std::string extract_token(const httplib::Request& req) {
    // Check Authorization header first
    auto auth = req.get_header_value("Authorization");
    if (auth.size() > 7 && auth.substr(0, 7) == "Bearer ") {
        return auth.substr(7);
    }

    // Check cookies
    auto cookie_header = req.get_header_value("Cookie");
    if (!cookie_header.empty()) {
        // Parse cookie string for "jwt=" value
        std::string search = "jwt=";
        auto pos = cookie_header.find(search);
        if (pos != std::string::npos) {
            auto start = pos + search.size();
            auto end = cookie_header.find(';', start);
            if (end == std::string::npos) {
                return cookie_header.substr(start);
            }
            return cookie_header.substr(start, end - start);
        }
    }

    return "";
}

// ============================================================================
// Helper: get authenticated user
// ============================================================================

static std::optional<User> get_current_user(const httplib::Request& req, AuthService& auth) {
    auto token = extract_token(req);
    if (token.empty()) return std::nullopt;
    return auth.get_current_user(token);
}

// ============================================================================
// Helper: send JSON response
// ============================================================================

static void json_response(httplib::Response& res, int status, const json& body) {
    res.status = status;
    res.set_content(body.dump(), "application/json; charset=utf-8");
}

// ============================================================================
// Helper: send error response
// ============================================================================

static void error_response(httplib::Response& res, int status,
                           const std::string& error, const std::string& message) {
    json body = {
        {"status", status},
        {"error", error},
        {"message", message},
        {"timestamp", current_iso_time()}
    };
    json_response(res, status, body);
}

// ============================================================================
// Helper: require authentication
// ============================================================================

static std::optional<User> require_auth(const httplib::Request& req,
                                        httplib::Response& res,
                                        AuthService& auth) {
    auto user = get_current_user(req, auth);
    if (!user) {
        error_response(res, 401, "Unauthorized", "Authentication required");
    }
    return user;
}

// ============================================================================
// Helper: require admin role
// ============================================================================

static std::optional<User> require_admin(const httplib::Request& req,
                                         httplib::Response& res,
                                         AuthService& auth) {
    auto user = require_auth(req, res, auth);
    if (!user) return std::nullopt;
    if (user->role != Role::ADMIN) {
        error_response(res, 403, "Forbidden", "Admin access required");
        return std::nullopt;
    }
    return user;
}

// ============================================================================
// Session storage for assistant chat history
// (ChatMessage is defined in assistant_service.hpp)
// ============================================================================

static std::map<std::string, std::vector<ChatMessage>> chat_sessions;
static std::mutex sessions_mutex;

// ============================================================================
// Main
// ============================================================================

int main(int argc, char* argv[]) {
    // Load config
    std::string config_path = "config.ini";
    if (argc > 1) config_path = argv[1];

    auto config = AppConfig::load(config_path);

    // Override with environment variables for Docker support
    const char* env;
    if ((env = std::getenv("DB_HOST"))) config.database.host = env;
    if ((env = std::getenv("DB_PORT"))) config.database.port = std::stoi(env);
    if ((env = std::getenv("DB_NAME"))) config.database.dbname = env;
    if ((env = std::getenv("DB_USER"))) config.database.user = env;
    if ((env = std::getenv("DB_PASSWORD"))) config.database.password = env;
    if ((env = std::getenv("SPRING_DATASOURCE_URL"))) {
        // Parse JDBC URL: jdbc:postgresql://host:port/dbname
        std::string url = env;
        auto pos = url.find("://");
        if (pos != std::string::npos) {
            url = url.substr(pos + 3);
            auto slash = url.find('/');
            auto colon = url.find(':');
            if (colon != std::string::npos && slash != std::string::npos && colon < slash) {
                config.database.host = url.substr(0, colon);
                config.database.port = std::stoi(url.substr(colon + 1, slash - colon - 1));
            } else if (slash != std::string::npos) {
                config.database.host = url.substr(0, slash);
            }
            if (slash != std::string::npos) {
                config.database.dbname = url.substr(slash + 1);
            }
        }
    }
    if ((env = std::getenv("SPRING_DATASOURCE_USERNAME"))) config.database.user = env;
    if ((env = std::getenv("SPRING_DATASOURCE_PASSWORD"))) config.database.password = env;
    if ((env = std::getenv("APP_JWT_SECRET"))) config.jwt.secret = env;

    // Build connection string and create pool
    auto conn_str = build_connection_string(config.database.host, config.database.port,
        config.database.dbname, config.database.user, config.database.password);

    std::cout << "Connecting to database: " << config.database.host << ":"
              << config.database.port << "/" << config.database.dbname << std::endl;

    ConnectionPool pool(conn_str, 5);
    Repositories repos(pool);
    JwtUtils jwt(config.jwt.secret, config.jwt.expiration_seconds);

    // Create services
    AuthService auth_svc(repos, jwt);
    CompanyProfileService profile_svc(repos);
    TaxService tax_svc(repos);
    TaxCalculatorService tax_calc_svc;
    TenderService tender_svc(repos);
    ProcurementService procurement_svc(repos);
    NotificationService notification_svc(repos);
    FavoriteService favorite_svc(repos);
    LegalReferenceService legal_svc(repos);
    AssistantService assistant_svc(config.assistant);

    // Load initial mock tenders
    tender_svc.load_initial_tenders();

    // Create HTTP server
    httplib::Server svr;

    // CORS middleware
    svr.set_pre_routing_handler([](const httplib::Request& req, httplib::Response& res) {
        res.set_header("Access-Control-Allow-Origin", "*");
        res.set_header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        res.set_header("Access-Control-Allow-Headers", "Content-Type, Authorization");
        res.set_header("Access-Control-Allow-Credentials", "true");
        if (req.method == "OPTIONS") {
            res.status = 204;
            return httplib::Server::HandlerResponse::Handled;
        }
        return httplib::Server::HandlerResponse::Unhandled;
    });

    // Serve the web UI (static files) from ./web, configurable via WEB_ROOT
    {
        const char* web_root_env = std::getenv("WEB_ROOT");
        std::string web_root = web_root_env ? web_root_env : "./web";
        if (!svr.set_mount_point("/", web_root)) {
            std::cerr << "Warning: web UI directory not found at " << web_root << std::endl;
        } else {
            std::cout << "Serving web UI from " << web_root << std::endl;
        }
    }

    // ========================================================================
    // Auth API
    // ========================================================================

    svr.Post("/api/auth/register", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto body = json::parse(req.body);
            std::string email = body.value("email", "");
            std::string password = body.value("password", "");
            std::string full_name = body.value("fullName", "");

            if (email.empty() || password.empty() || full_name.empty()) {
                error_response(res, 400, "Bad Request", "Email, password and fullName are required");
                return;
            }

            auto result = auth_svc.register_user(email, password, full_name);
            std::string token = result["token"];
            res.set_header("Set-Cookie", "jwt=" + token + "; HttpOnly; Path=/; Max-Age=86400");
            json_response(res, 200, result);
        } catch (const std::runtime_error& e) {
            error_response(res, 400, "Bad Request", e.what());
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Post("/api/auth/login", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto body = json::parse(req.body);
            std::string email = body.value("email", "");
            std::string password = body.value("password", "");

            if (email.empty() || password.empty()) {
                error_response(res, 400, "Bad Request", "Email and password required");
                return;
            }

            auto result = auth_svc.login(email, password);
            std::string token = result["token"];
            res.set_header("Set-Cookie", "jwt=" + token + "; HttpOnly; Path=/; Max-Age=86400");
            json_response(res, 200, result);
        } catch (const std::runtime_error& e) {
            error_response(res, 401, "Unauthorized", e.what());
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    // ========================================================================
    // Dashboard API
    // ========================================================================

    svr.Get("/api/dashboard", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            json response;
            response["user"] = user_to_json(*user);

            auto profile = profile_svc.find_by_user_id(user->id);
            if (profile) {
                response["profile"] = company_profile_to_json(*profile);

                // Get current regimes
                auto regimes = profile_svc.get_current_regimes(profile->id);
                json regimes_json = json::array();
                for (const auto& r : regimes) {
                    regimes_json.push_back(company_tax_regime_to_json(r));
                }
                response["currentRegimes"] = regimes_json;
            } else {
                response["profile"] = nullptr;
                response["currentRegimes"] = json::array();
            }

            // Get user checklists
            auto checklists = repos.find_user_checklists(user->id);
            json checklists_json = json::array();
            for (const auto& cl : checklists) {
                checklists_json.push_back(checklist_to_json(cl));
            }
            response["checklists"] = checklists_json;

            json_response(res, 200, response);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    // ========================================================================
    // Profile API
    // ========================================================================

    svr.Get("/api/profile/me", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;
            json_response(res, 200, user_to_json(*user));
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get("/api/profile", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            auto profile = profile_svc.find_by_user_id(user->id);
            if (!profile) {
                error_response(res, 404, "Not Found", "Company profile not found");
                return;
            }
            json_response(res, 200, company_profile_to_json(*profile));
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Post("/api/profile", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            auto body = json::parse(req.body);
            std::string company_name = body.value("companyName", "");
            std::string company_type = body.value("companyType", "");
            std::string inn = body.value("inn", "");
            std::string industry = body.value("industry", "");

            std::optional<int> employees;
            if (body.contains("employeesCount") && !body["employeesCount"].is_null()) {
                employees = body["employeesCount"].get<int>();
            }

            std::optional<double> revenue;
            if (body.contains("annualRevenue") && !body["annualRevenue"].is_null()) {
                revenue = body["annualRevenue"].get<double>();
            }

            auto profile = profile_svc.save_or_update(user->id, company_name, company_type,
                                                       inn, industry, employees, revenue);
            json_response(res, 200, company_profile_to_json(profile));
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    // ========================================================================
    // Tax API
    // ========================================================================

    svr.Get("/api/tax/regimes", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto regimes = tax_svc.get_all_regimes();
            json arr = json::array();
            for (const auto& r : regimes) {
                arr.push_back(tax_regime_to_json(r));
            }
            json_response(res, 200, arr);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get(R"(/api/tax/regimes/(.+))", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            std::string code = req.matches[1];
            auto regime = tax_svc.find_by_code(code);
            if (!regime) {
                error_response(res, 404, "Not Found", "Tax regime not found: " + code);
                return;
            }

            json response = tax_regime_to_json(*regime);

            // Get obligations
            auto obligations = tax_svc.get_obligations(regime->id);
            json obligs_json = json::array();
            for (const auto& o : obligations) {
                obligs_json.push_back(tax_obligation_to_json(o));
            }
            response["obligations"] = obligs_json;

            // Get deadlines
            auto deadlines = tax_svc.get_template_deadlines(regime->id);
            json deadlines_json = json::array();
            for (const auto& d : deadlines) {
                deadlines_json.push_back(deadline_to_json(d));
            }
            response["deadlines"] = deadlines_json;

            json_response(res, 200, response);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get("/api/tax/recommend", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            std::optional<std::string> company_type;
            std::optional<int> employees;
            std::optional<double> revenue;

            auto ct = req.get_param_value("companyType");
            if (!ct.empty()) company_type = ct;

            auto emp = req.get_param_value("employees");
            if (!emp.empty()) employees = std::stoi(emp);

            auto rev = req.get_param_value("revenue");
            if (!rev.empty()) revenue = std::stod(rev);

            auto regimes = tax_svc.recommend(company_type, employees, revenue);
            json arr = json::array();
            for (const auto& r : regimes) {
                arr.push_back(tax_regime_to_json(r));
            }
            json_response(res, 200, arr);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get("/api/tax/current", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            auto profile = profile_svc.find_by_user_id(user->id);
            if (!profile) {
                json_response(res, 200, json::array());
                return;
            }

            auto regimes = profile_svc.get_current_regimes(profile->id);
            json arr = json::array();
            for (const auto& r : regimes) {
                arr.push_back(company_tax_regime_to_json(r));
            }
            json_response(res, 200, arr);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Post("/api/tax/set-regime", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            auto body = json::parse(req.body);
            std::string regime_code = body.value("regimeCode", "");
            if (regime_code.empty()) {
                error_response(res, 400, "Bad Request", "regimeCode is required");
                return;
            }

            auto profile = profile_svc.find_by_user_id(user->id);
            if (!profile) {
                error_response(res, 400, "Bad Request", "Company profile not found. Create a profile first.");
                return;
            }

            profile_svc.set_tax_regime(profile->id, regime_code);
            json_response(res, 200, {{"message", "Tax regime set successfully"}});
        } catch (const std::runtime_error& e) {
            error_response(res, 400, "Bad Request", e.what());
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get("/api/tax/deadlines", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            auto profile = profile_svc.find_by_user_id(user->id);
            if (!profile) {
                json_response(res, 200, json::array());
                return;
            }

            auto deadlines = tax_svc.get_company_deadlines(profile->id);
            json arr = json::array();
            for (const auto& d : deadlines) {
                arr.push_back(deadline_to_json(d));
            }
            json_response(res, 200, arr);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get("/api/tax/calculator", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto revenue_str = req.get_param_value("revenue");
            auto expenses_str = req.get_param_value("expenses");
            auto employees_str = req.get_param_value("employees");
            auto avg_salary_str = req.get_param_value("avgSalary");
            auto is_ip_str = req.get_param_value("isIp");

            TaxCalcInput input;
            input.revenue = revenue_str.empty() ? 0.0 : std::stod(revenue_str);
            input.expenses = expenses_str.empty() ? 0.0 : std::stod(expenses_str);
            input.employees = employees_str.empty() ? 0 : std::stoi(employees_str);
            input.avg_salary = avg_salary_str.empty() ? 0.0 : std::stod(avg_salary_str);
            input.is_ip = (is_ip_str.empty() || is_ip_str == "true");

            auto results = tax_calc_svc.calculate(input);
            json arr = json::array();
            for (const auto& r : results) {
                arr.push_back({
                    {"regimeCode", r.regime_code},
                    {"regimeName", r.regime_name},
                    {"taxAmount", r.tax_amount},
                    {"contributions", r.contributions},
                    {"totalLoad", r.total_load},
                    {"effectiveRate", r.effective_rate},
                    {"breakdown", r.breakdown},
                    {"applicable", r.applicable},
                    {"notApplicableReason", r.not_applicable_reason},
                    {"best", r.best}
                });
            }
            json_response(res, 200, arr);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    // ========================================================================
    // Procurement API
    // ========================================================================

    svr.Get("/api/procurement/templates", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto templates = repos.find_template_checklists();
            json arr = json::array();
            for (const auto& t : templates) {
                arr.push_back(checklist_to_json(t));
            }
            json_response(res, 200, arr);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get("/api/procurement/scenarios", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            // If user is authenticated and has MSP profile, show MSP scenarios
            auto user = get_current_user(req, auth_svc);
            std::vector<ProcurementScenario> scenarios;

            if (user) {
                auto profile = profile_svc.find_by_user_id(user->id);
                if (profile && profile->is_msp) {
                    scenarios = repos.find_msp_scenarios();
                    if (scenarios.empty()) {
                        scenarios = repos.find_all_scenarios();
                    }
                } else {
                    scenarios = repos.find_all_scenarios();
                }
            } else {
                scenarios = repos.find_all_scenarios();
            }

            json arr = json::array();
            for (const auto& s : scenarios) {
                arr.push_back(scenario_to_json(s));
            }
            json_response(res, 200, arr);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get(R"(/api/procurement/scenarios/(\d+))", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            int64_t id = std::stoll(req.matches[1]);
            auto scenario = repos.find_scenario_by_id(id);
            if (!scenario) {
                error_response(res, 404, "Not Found", "Scenario not found");
                return;
            }

            json response = scenario_to_json(*scenario);

            // Get risks for this scenario
            auto risks = repos.find_risks_by_scenario(id);
            json risks_json = json::array();
            for (const auto& r : risks) {
                risks_json.push_back(risk_card_to_json(r));
            }
            response["risks"] = risks_json;

            // Get template checklists for this scenario
            auto templates = repos.find_template_checklists();
            json templates_json = json::array();
            for (const auto& t : templates) {
                if (t.scenario_id.has_value() && t.scenario_id.value() == id) {
                    templates_json.push_back(checklist_to_json(t));
                }
            }
            response["templates"] = templates_json;

            json_response(res, 200, response);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get("/api/procurement/checklists", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            auto checklists = repos.find_user_checklists(user->id);
            json arr = json::array();
            for (const auto& cl : checklists) {
                arr.push_back(checklist_to_json(cl));
            }
            json_response(res, 200, arr);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get(R"(/api/procurement/checklists/(\d+))", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            int64_t id = std::stoll(req.matches[1]);
            auto checklist = repos.find_checklist_by_id(id);
            if (!checklist) {
                error_response(res, 404, "Not Found", "Checklist not found");
                return;
            }

            // Verify ownership
            if (checklist->user_id.has_value() && checklist->user_id.value() != user->id) {
                error_response(res, 403, "Forbidden", "Access denied");
                return;
            }

            json response = checklist_to_json(*checklist);
            json_response(res, 200, response);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Post(R"(/api/procurement/checklists/copy/(\d+))", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            int64_t template_id = std::stoll(req.matches[1]);
            auto tmpl = repos.find_checklist_by_id(template_id);
            if (!tmpl) {
                error_response(res, 404, "Not Found", "Template not found");
                return;
            }

            // Copy the template as a new user checklist
            Checklist new_cl;
            new_cl.user_id = user->id;
            new_cl.scenario_id = tmpl->scenario_id;
            new_cl.title = tmpl->title;
            new_cl.description = tmpl->description;
            new_cl.is_template = false;
            new_cl.is_completed = false;

            // Copy steps
            for (const auto& step : tmpl->steps) {
                ChecklistStep new_step;
                new_step.step_order = step.step_order;
                new_step.title = step.title;
                new_step.description = step.description;
                new_step.hint = step.hint;
                new_step.is_completed = false;
                new_cl.steps.push_back(new_step);
            }

            auto saved = repos.save_checklist(new_cl);
            json_response(res, 200, checklist_to_json(saved));
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Post(R"(/api/procurement/checklists/step/(\d+)/toggle)", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            int64_t step_id = std::stoll(req.matches[1]);
            auto step = repos.find_step_by_id(step_id);
            if (!step) {
                error_response(res, 404, "Not Found", "Step not found");
                return;
            }

            // Verify ownership through checklist
            auto checklist = repos.find_checklist_by_id(step->checklist_id);
            if (!checklist || (checklist->user_id.has_value() && checklist->user_id.value() != user->id)) {
                error_response(res, 403, "Forbidden", "Access denied");
                return;
            }

            // Toggle the step
            bool new_state = !step->is_completed;
            repos.toggle_step(step_id, new_state);

            // Check if all steps are completed and update checklist
            auto steps = repos.find_steps_by_checklist(step->checklist_id);
            bool all_completed = true;
            for (const auto& s : steps) {
                if (s.id == step_id) {
                    if (!new_state) { all_completed = false; break; }
                } else {
                    if (!s.is_completed) { all_completed = false; break; }
                }
            }
            repos.update_checklist_completion(step->checklist_id, all_completed);

            json_response(res, 200, {{"completed", new_state}, {"checklistCompleted", all_completed}});
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    // ========================================================================
    // Tender API
    // ========================================================================

    svr.Get("/api/tenders/stats", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto result = tender_svc.get_stats();
            json_response(res, 200, result);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get(R"(/api/tenders/(\d+))", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            int64_t id = std::stoll(req.matches[1]);
            auto tender = repos.find_tender_by_id(id);
            if (!tender) {
                error_response(res, 404, "Not Found", "Tender not found");
                return;
            }
            json_response(res, 200, tender_detail_to_json(*tender));
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get("/api/tenders", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            std::string query = req.get_param_value("query");
            std::string law_type = req.get_param_value("lawType");
            std::string status = req.get_param_value("status");
            std::string region = req.get_param_value("region");
            std::string category = req.get_param_value("category");
            auto price_from_str = req.get_param_value("priceFrom");
            auto price_to_str = req.get_param_value("priceTo");
            std::optional<double> price_from, price_to;
            if (!price_from_str.empty()) price_from = std::stod(price_from_str);
            if (!price_to_str.empty()) price_to = std::stod(price_to_str);
            bool msp_only = req.get_param_value("mspOnly") == "true";
            int page = 0, size = 20;
            auto page_str = req.get_param_value("page");
            auto size_str = req.get_param_value("size");
            if (!page_str.empty()) page = std::stoi(page_str);
            if (!size_str.empty()) size = std::stoi(size_str);
            std::string sort = req.get_param_value("sort");
            if (sort.empty()) sort = "published_at DESC";

            auto response = tender_svc.search(query, law_type, status, region, category,
                                              price_from, price_to, msp_only, page, size, sort);
            json_response(res, 200, response);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    // ========================================================================
    // Notification API
    // ========================================================================

    svr.Get("/api/notifications/unread", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            auto notifications = repos.find_unread_notifications(user->id);
            json arr = json::array();
            for (const auto& n : notifications) {
                arr.push_back(notification_to_json(n));
            }
            json_response(res, 200, arr);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get("/api/notifications/count", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            auto count = repos.count_unread(user->id);
            json_response(res, 200, {{"count", count}});
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Post("/api/notifications/read-all", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            repos.mark_all_as_read(user->id);
            json_response(res, 200, {{"message", "All notifications marked as read"}});
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Post(R"(/api/notifications/(\d+)/read)", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            int64_t id = std::stoll(req.matches[1]);
            repos.mark_as_read(id);
            json_response(res, 200, {{"message", "Notification marked as read"}});
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Delete(R"(/api/notifications/(\d+))", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            int64_t id = std::stoll(req.matches[1]);
            repos.delete_notification(id);
            json_response(res, 200, {{"message", "Notification deleted"}});
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get("/api/notifications", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            auto notifications = repos.find_user_notifications(user->id);
            json arr = json::array();
            for (const auto& n : notifications) {
                arr.push_back(notification_to_json(n));
            }
            json_response(res, 200, arr);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    // ========================================================================
    // Favorite API
    // ========================================================================

    svr.Get("/api/favorites/check", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            std::string entity_type = req.get_param_value("entityType");
            auto entity_id_str = req.get_param_value("entityId");

            if (entity_type.empty() || entity_id_str.empty()) {
                error_response(res, 400, "Bad Request", "entityType and entityId required");
                return;
            }

            int64_t entity_id = std::stoll(entity_id_str);
            bool is_fav = repos.is_favorite(user->id, entity_type, entity_id);
            json_response(res, 200, {{"favorite", is_fav}});
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    // Enrich a UserFavorite with the linked entity's display name
    auto enrich_favorite = [&](const UserFavorite& f) -> json {
        json j = favorite_to_json(f);
        std::string name;
        std::string entity_url;
        if (f.entity_type == "TAX_REGIME") {
            if (auto e = repos.find_tax_regime_by_id(f.entity_id)) {
                name = e->name;
                entity_url = "/tax.html#regime-" + e->code;
            }
        } else if (f.entity_type == "PROCUREMENT") {
            if (auto e = repos.find_scenario_by_id(f.entity_id)) {
                name = e->title;
                entity_url = "/procurement.html#scenario-" + std::to_string(e->id);
            }
        } else if (f.entity_type == "RISK") {
            if (auto e = repos.find_risk_by_id(f.entity_id)) name = e->title;
        } else if (f.entity_type == "TENDER") {
            if (auto e = repos.find_tender_by_id(f.entity_id)) {
                name = e->title;
                entity_url = "/tenders.html#tender-" + std::to_string(e->id);
            }
        }
        j["entityName"] = name.empty() ? json(nullptr) : json(name);
        j["entityUrl"] = entity_url.empty() ? json(nullptr) : json(entity_url);
        return j;
    };

    svr.Get(R"(/api/favorites/type/(.+))", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            std::string type = req.matches[1];
            auto favorites = repos.find_user_favorites_by_type(user->id, type);
            json arr = json::array();
            for (const auto& f : favorites) {
                arr.push_back(enrich_favorite(f));
            }
            json_response(res, 200, arr);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Post("/api/favorites/toggle", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            auto body = json::parse(req.body);
            std::string entity_type = body.value("entityType", "");
            int64_t entity_id = body.value("entityId", (int64_t)0);

            if (entity_type.empty() || entity_id == 0) {
                error_response(res, 400, "Bad Request", "entityType and entityId required");
                return;
            }

            bool is_fav = repos.is_favorite(user->id, entity_type, entity_id);
            if (is_fav) {
                repos.remove_favorite(user->id, entity_type, entity_id);
                json_response(res, 200, {{"added", false}});
            } else {
                repos.add_favorite(user->id, entity_type, entity_id);
                json_response(res, 200, {{"added", true}});
            }
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get("/api/favorites", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            auto favorites = repos.find_user_favorites(user->id);
            json arr = json::array();
            for (const auto& f : favorites) {
                arr.push_back(enrich_favorite(f));
            }
            json_response(res, 200, arr);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    // ========================================================================
    // Legal Reference API
    // ========================================================================

    svr.Get(R"(/api/legal/type/(.+))", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            std::string type = req.matches[1];
            auto refs = repos.find_references_by_type(type);
            json arr = json::array();
            for (const auto& r : refs) {
                arr.push_back(legal_reference_to_json(r));
            }
            json_response(res, 200, arr);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get("/api/legal", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            std::string entity_type = req.get_param_value("entityType");
            auto entity_id_str = req.get_param_value("entityId");

            if (entity_type.empty() || entity_id_str.empty()) {
                error_response(res, 400, "Bad Request", "entityType and entityId query params required");
                return;
            }

            int64_t entity_id = std::stoll(entity_id_str);
            auto refs = repos.find_references(entity_type, entity_id);
            json arr = json::array();
            for (const auto& r : refs) {
                arr.push_back(legal_reference_to_json(r));
            }
            json_response(res, 200, arr);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    // ========================================================================
    // Search API
    // ========================================================================

    svr.Get("/api/search", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            std::string q = req.get_param_value("q");
            if (q.empty()) {
                json_response(res, 200, {{"regimes", json::array()}, {"scenarios", json::array()}});
                return;
            }

            std::string q_lower = q;
            std::transform(q_lower.begin(), q_lower.end(), q_lower.begin(), ::tolower);

            // Search tax regimes
            auto all_regimes = tax_svc.get_all_regimes();
            json regimes_json = json::array();
            for (const auto& r : all_regimes) {
                std::string name_lower = r.name;
                std::transform(name_lower.begin(), name_lower.end(), name_lower.begin(), ::tolower);
                std::string desc_lower = r.description;
                std::transform(desc_lower.begin(), desc_lower.end(), desc_lower.begin(), ::tolower);

                if (name_lower.find(q_lower) != std::string::npos ||
                    desc_lower.find(q_lower) != std::string::npos) {
                    regimes_json.push_back(tax_regime_to_json(r));
                }
            }

            // Search scenarios
            auto all_scenarios = repos.find_all_scenarios();
            json scenarios_json = json::array();
            for (const auto& s : all_scenarios) {
                std::string title_lower = s.title;
                std::transform(title_lower.begin(), title_lower.end(), title_lower.begin(), ::tolower);
                std::string desc_lower = s.description;
                std::transform(desc_lower.begin(), desc_lower.end(), desc_lower.begin(), ::tolower);

                if (title_lower.find(q_lower) != std::string::npos ||
                    desc_lower.find(q_lower) != std::string::npos) {
                    scenarios_json.push_back(scenario_to_json(s));
                }
            }

            json_response(res, 200, {{"regimes", regimes_json}, {"scenarios", scenarios_json}});
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    // ========================================================================
    // Assistant API
    // ========================================================================

    svr.Post("/api/assistant/chat", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            auto body = json::parse(req.body);
            std::string message = body.value("message", "");
            if (message.empty()) {
                error_response(res, 400, "Bad Request", "message is required");
                return;
            }

            std::string session_key = user->email;

            // chat() appends both the user message and the assistant reply to history
            json result;
            {
                std::lock_guard<std::mutex> lock(sessions_mutex);
                auto& history = chat_sessions[session_key];
                result = assistant_svc.chat(message, history);
                // Keep only the last 50 messages per session
                if (history.size() > 50) {
                    history.erase(history.begin(), history.end() - 50);
                }
            }

            json_response(res, 200, result);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get("/api/assistant/history", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            std::string session_key = user->email;
            json arr = json::array();

            {
                std::lock_guard<std::mutex> lock(sessions_mutex);
                auto it = chat_sessions.find(session_key);
                if (it != chat_sessions.end()) {
                    for (const auto& msg : it->second) {
                        arr.push_back({
                            {"role", msg.role},
                            {"content", msg.content},
                            {"timestamp", msg.timestamp}
                        });
                    }
                }
            }

            json_response(res, 200, arr);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Delete("/api/assistant/history", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_auth(req, res, auth_svc);
            if (!user) return;

            std::string session_key = user->email;
            {
                std::lock_guard<std::mutex> lock(sessions_mutex);
                chat_sessions.erase(session_key);
            }

            json_response(res, 200, {{"message", "Chat history cleared"}});
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get("/api/assistant/status", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto status = assistant_svc.get_status();
            json_response(res, 200, status);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    // ========================================================================
    // Admin API
    // ========================================================================

    svr.Get("/api/admin/stats", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_admin(req, res, auth_svc);
            if (!user) return;

            auto users = repos.find_all_users();
            auto regimes = tax_svc.get_all_regimes();
            auto scenarios = repos.find_all_scenarios();
            auto tender_count = repos.count_tenders();

            json_response(res, 200, {
                {"userCount", (int64_t)users.size()},
                {"regimeCount", (int64_t)regimes.size()},
                {"scenarioCount", (int64_t)scenarios.size()},
                {"tenderCount", tender_count}
            });
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get("/api/admin/users", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_admin(req, res, auth_svc);
            if (!user) return;

            auto users = repos.find_all_users();
            json arr = json::array();
            for (const auto& u : users) {
                arr.push_back(user_to_json(u));
            }
            json_response(res, 200, arr);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Post(R"(/api/admin/users/(\d+)/toggle-role)", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto admin = require_admin(req, res, auth_svc);
            if (!admin) return;

            int64_t target_id = std::stoll(req.matches[1]);
            auto target_user = repos.find_user_by_id(target_id);
            if (!target_user) {
                error_response(res, 404, "Not Found", "User not found");
                return;
            }

            std::string new_role = (target_user->role == Role::ADMIN) ? "USER" : "ADMIN";
            repos.update_user_role(target_id, new_role);

            json_response(res, 200, {{"message", "Role updated to " + new_role}});
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Post(R"(/api/admin/users/(\d+)/delete)", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto admin = require_admin(req, res, auth_svc);
            if (!admin) return;

            int64_t target_id = std::stoll(req.matches[1]);

            // Cannot delete self
            if (target_id == admin->id) {
                error_response(res, 400, "Bad Request", "Cannot delete yourself");
                return;
            }

            auto target_user = repos.find_user_by_id(target_id);
            if (!target_user) {
                error_response(res, 404, "Not Found", "User not found");
                return;
            }

            repos.delete_user(target_id);
            json_response(res, 200, {{"message", "User deleted"}});
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get("/api/admin/regimes", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_admin(req, res, auth_svc);
            if (!user) return;

            auto regimes = tax_svc.get_all_regimes();
            json arr = json::array();
            for (const auto& r : regimes) {
                arr.push_back(tax_regime_to_json(r));
            }
            json_response(res, 200, arr);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Post("/api/admin/regimes/save", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_admin(req, res, auth_svc);
            if (!user) return;

            auto body = json::parse(req.body);
            TaxRegime regime;
            regime.id = body.value("id", (int64_t)0);
            regime.code = body.value("code", "");
            regime.name = body.value("name", "");
            regime.description = body.value("description", "");
            regime.conditions = body.value("conditions", "");
            regime.nk_ref = body.value("nkRef", "");

            if (regime.code.empty() || regime.name.empty()) {
                error_response(res, 400, "Bad Request", "code and name are required");
                return;
            }

            auto saved = repos.save_tax_regime(regime);
            json_response(res, 200, tax_regime_to_json(saved));
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get("/api/admin/scenarios", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_admin(req, res, auth_svc);
            if (!user) return;

            auto scenarios = repos.find_all_scenarios();
            json arr = json::array();
            for (const auto& s : scenarios) {
                arr.push_back(scenario_to_json(s));
            }
            json_response(res, 200, arr);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Post("/api/admin/scenarios/save", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_admin(req, res, auth_svc);
            if (!user) return;

            auto body = json::parse(req.body);
            ProcurementScenario scenario;
            scenario.id = body.value("id", (int64_t)0);
            scenario.law_type = body.value("lawType", "");
            scenario.title = body.value("title", "");
            scenario.description = body.value("description", "");
            scenario.msp_only = body.value("mspOnly", false);

            if (body.contains("amountMin") && !body["amountMin"].is_null()) {
                scenario.amount_min = body["amountMin"].get<double>();
            }
            if (body.contains("amountMax") && !body["amountMax"].is_null()) {
                scenario.amount_max = body["amountMax"].get<double>();
            }

            if (scenario.law_type.empty() || scenario.title.empty()) {
                error_response(res, 400, "Bad Request", "lawType and title are required");
                return;
            }

            auto saved = repos.save_scenario(scenario);
            json_response(res, 200, scenario_to_json(saved));
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Get("/api/admin/risks", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_admin(req, res, auth_svc);
            if (!user) return;

            auto risks = repos.find_all_risks();
            auto scenarios = repos.find_all_scenarios();

            // Build scenario_id -> title map for nice display
            json scenarios_map = json::object();
            for (const auto& s : scenarios) {
                scenarios_map[std::to_string(s.id)] = s.title;
            }

            json risks_json = json::array();
            for (const auto& r : risks) {
                json rj = risk_card_to_json(r);
                rj["scenarioId"] = r.scenario_id;
                auto it = scenarios_map.find(std::to_string(r.scenario_id));
                rj["scenarioTitle"] = (it != scenarios_map.end()) ? *it : json(nullptr);
                risks_json.push_back(rj);
            }

            json response;
            response["risks"] = risks_json;
            json scenarios_arr = json::array();
            for (const auto& s : scenarios) scenarios_arr.push_back(scenario_to_json(s));
            response["scenarios"] = scenarios_arr;

            json_response(res, 200, response);
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Post("/api/admin/risks/save", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_admin(req, res, auth_svc);
            if (!user) return;

            auto body = json::parse(req.body);
            RiskCard risk;
            risk.id = body.value("id", (int64_t)0);
            risk.scenario_id = body.value("scenarioId", (int64_t)0);
            risk.title = body.value("title", "");
            risk.risk_type = body.value("riskType", "");
            risk.description = body.value("description", "");
            risk.consequence = body.value("consequence", "");
            risk.recommendation = body.value("recommendation", "");

            if (risk.scenario_id == 0 || risk.title.empty()) {
                error_response(res, 400, "Bad Request", "scenarioId and title are required");
                return;
            }

            auto saved = repos.save_risk(risk);
            json_response(res, 200, risk_card_to_json(saved));
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    svr.Post(R"(/api/admin/risks/(\d+)/delete)", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto user = require_admin(req, res, auth_svc);
            if (!user) return;

            int64_t id = std::stoll(req.matches[1]);
            repos.delete_risk(id);
            json_response(res, 200, {{"message", "Risk deleted"}});
        } catch (const std::exception& e) {
            error_response(res, 500, "Internal Server Error", e.what());
        }
    });

    // ========================================================================
    // Health / API info endpoint
    // ========================================================================

    svr.Get("/api", [&](const httplib::Request& req, httplib::Response& res) {
        json info = {
            {"application", "BizSupport API"},
            {"version", "1.0.0"},
            {"language", "C++"},
            {"framework", "cpp-httplib"},
            {"status", "running"},
            {"timestamp", current_iso_time()}
        };
        json_response(res, 200, info);
    });

    // ========================================================================
    // Start notification reminder thread
    // ========================================================================

    std::thread reminder_thread([&]() {
        while (true) {
            std::this_thread::sleep_for(std::chrono::hours(1));
            try {
                notification_svc.generate_reminders();
                std::cout << "Generated deadline reminders" << std::endl;
            } catch (const std::exception& e) {
                std::cerr << "Reminder generation error: " << e.what() << std::endl;
            }
        }
    });
    reminder_thread.detach();

    // ========================================================================
    // Start the server
    // ========================================================================

    std::cout << "BizSupport C++ server starting on port " << config.server.port << std::endl;
    std::cout << "API docs: http://localhost:" << config.server.port << "/api" << std::endl;
    svr.listen("0.0.0.0", config.server.port);

    return 0;
}
