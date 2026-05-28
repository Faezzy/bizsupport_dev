#pragma once
#include "config.hpp"
#include <nlohmann/json.hpp>
#include <string>
#include <vector>
#include <chrono>
#include <ctime>
#include <algorithm>
#define CPPHTTPLIB_OPENSSL_SUPPORT
#include <httplib.h>

struct ChatMessage {
    std::string role;      // "user" or "assistant" or "system"
    std::string content;
    std::string timestamp; // ISO format
};

class AssistantService {
public:
    AssistantService(const AssistantConfig& config) : config_(config) {}

    // Send message, get reply. history is the conversation context.
    nlohmann::json chat(const std::string& message, std::vector<ChatMessage>& history) {
        // Build messages array with system prompt
        nlohmann::json messages = nlohmann::json::array();

        // System prompt (specialized for Russian business)
        messages.push_back({
            {"role", "system"},
            {"content", "\xD0\xA2\xD1\x8B \xe2\x80\x94 \xD0\x98\xD0\x98-\xD0\xB0\xD1\x81\xD1\x81\xD0\xB8\xD1\x81\xD1\x82\xD0\xB5\xD0\xBD\xD1\x82 "
                        "\xD0\xBF\xD0\xBB\xD0\xB0\xD1\x82\xD1\x84\xD0\xBE\xD1\x80\xD0\xBC\xD1\x8B BizSupport, "
                        "\xD1\x81\xD0\xBF\xD0\xB5\xD1\x86\xD0\xB8\xD0\xB0\xD0\xBB\xD0\xB8\xD0\xB7\xD0\xB8\xD1\x80\xD1\x83\xD1\x8E\xD1\x89\xD0\xB8\xD0\xB9\xD1\x81\xD1\x8F "
                        "\xD0\xBD\xD0\xB0 \xD0\xBF\xD0\xBE\xD0\xB4\xD0\xB4\xD0\xB5\xD1\x80\xD0\xB6\xD0\xBA\xD0\xB5 "
                        "\xD0\xBC\xD0\xB0\xD0\xBB\xD0\xBE\xD0\xB3\xD0\xBE \xD0\xB8 "
                        "\xD1\x81\xD1\x80\xD0\xB5\xD0\xB4\xD0\xBD\xD0\xB5\xD0\xB3\xD0\xBE "
                        "\xD0\xB1\xD0\xB8\xD0\xB7\xD0\xBD\xD0\xB5\xD1\x81\xD0\xB0 "
                        "\xD0\xB2 \xD0\xA0\xD0\xBE\xD1\x81\xD1\x81\xD0\xB8\xD0\xB8. "
                        "\xD0\xA2\xD1\x8B \xD1\x80\xD0\xB0\xD0\xB7\xD0\xB1\xD0\xB8\xD1\x80\xD0\xB0\xD0\xB5\xD1\x88\xD1\x8C\xD1\x81\xD1\x8F "
                        "\xD0\xB2: \xD0\xBD\xD0\xB0\xD0\xBB\xD0\xBE\xD0\xB3\xD0\xBE\xD0\xB2\xD1\x8B\xD1\x85 "
                        "\xD1\x80\xD0\xB5\xD0\xB6\xD0\xB8\xD0\xBC\xD0\xB0\xD1\x85 "
                        "(\xD0\xA3\xD0\xA1\xD0\x9D, \xD0\x9E\xD0\xA1\xD0\x9D\xD0\x9E, "
                        "\xD0\x9F\xD0\xA1\xD0\x9D, \xD0\x9D\xD0\x9F\xD0\x94), "
                        "\xD0\xB3\xD0\xBE\xD1\x81\xD1\x83\xD0\xB4\xD0\xB0\xD1\x80\xD1\x81\xD1\x82\xD0\xB2\xD0\xB5\xD0\xBD\xD0\xBD\xD1\x8B\xD1\x85 "
                        "\xD0\xB7\xD0\xB0\xD0\xBA\xD1\x83\xD0\xBF\xD0\xBA\xD0\xB0\xD1\x85 "
                        "(44-\xD0\xA4\xD0\x97, 223-\xD0\xA4\xD0\x97), "
                        "\xD1\x80\xD0\xB5\xD0\xB3\xD0\xB8\xD1\x81\xD1\x82\xD1\x80\xD0\xB0\xD1\x86\xD0\xB8\xD0\xB8 "
                        "\xD0\xB1\xD0\xB8\xD0\xB7\xD0\xBD\xD0\xB5\xD1\x81\xD0\xB0, "
                        "\xD0\xB1\xD1\x83\xD1\x85\xD0\xB3\xD0\xB0\xD0\xBB\xD1\x82\xD0\xB5\xD1\x80\xD1\x81\xD0\xBA\xD0\xBE\xD0\xBC "
                        "\xD1\x83\xD1\x87\xD1\x91\xD1\x82\xD0\xB5, "
                        "\xD1\x82\xD1\x80\xD1\x83\xD0\xB4\xD0\xBE\xD0\xB2\xD0\xBE\xD0\xBC "
                        "\xD0\xBF\xD1\x80\xD0\xB0\xD0\xB2\xD0\xB5. "
                        "\xD0\x9E\xD1\x82\xD0\xB2\xD0\xB5\xD1\x87\xD0\xB0\xD0\xB9 "
                        "\xD0\xBA\xD1\x80\xD0\xB0\xD1\x82\xD0\xBA\xD0\xBE, "
                        "\xD0\xBF\xD0\xBE \xD0\xB4\xD0\xB5\xD0\xBB\xD1\x83, "
                        "\xD1\x81\xD0\xBE \xD1\x81\xD1\x81\xD1\x8B\xD0\xBB\xD0\xBA\xD0\xB0\xD0\xBC\xD0\xB8 "
                        "\xD0\xBD\xD0\xB0 \xD0\xB7\xD0\xB0\xD0\xBA\xD0\xBE\xD0\xBD\xD1\x8B. "
                        "\xD0\x95\xD1\x81\xD0\xBB\xD0\xB8 "
                        "\xD0\xBD\xD0\xB5 \xD1\x83\xD0\xB2\xD0\xB5\xD1\x80\xD0\xB5\xD0\xBD "
                        "\xe2\x80\x94 \xD1\x82\xD0\xB0\xD0\xBA \xD0\xB8 "
                        "\xD1\x81\xD0\xBA\xD0\xB0\xD0\xB6\xD0\xB8."}
            // "Ты -- ИИ-ассистент платформы BizSupport, специализирующийся на поддержке
            //  малого и среднего бизнеса в России. Ты разбираешься в: налоговых режимах
            //  (УСН, ОСНО, ПСН, НПД), государственных закупках (44-ФЗ, 223-ФЗ),
            //  регистрации бизнеса, бухгалтерском учёте, трудовом праве.
            //  Отвечай кратко, по делу, со ссылками на законы. Если не уверен -- так и скажи."
        });

        // Add history (last 10 messages)
        int start = std::max(0, static_cast<int>(history.size()) - 10);
        for (int i = start; i < static_cast<int>(history.size()); i++) {
            messages.push_back({{"role", history[i].role}, {"content", history[i].content}});
        }

        // Add current message
        messages.push_back({{"role", "user"}, {"content", message}});

        // Build request body
        nlohmann::json request_body = {
            {"model", config_.model},
            {"messages", messages},
            {"max_tokens", config_.max_tokens},
            {"temperature", config_.temperature}
        };

        try {
            // Parse base URL to get host and path
            std::string url = config_.base_url;
            // Remove trailing slash
            if (!url.empty() && url.back() == '/') url.pop_back();

            // Parse URL components
            std::string scheme, host, base_path;
            int port = 443;

            size_t scheme_end = url.find("://");
            if (scheme_end != std::string::npos) {
                scheme = url.substr(0, scheme_end);
                url = url.substr(scheme_end + 3);
            } else {
                scheme = "https";
            }

            size_t path_start = url.find('/');
            if (path_start != std::string::npos) {
                host = url.substr(0, path_start);
                base_path = url.substr(path_start);
            } else {
                host = url;
                base_path = "";
            }

            // Check for port in host
            size_t colon = host.find(':');
            if (colon != std::string::npos) {
                port = std::stoi(host.substr(colon + 1));
                host = host.substr(0, colon);
            } else {
                port = (scheme == "https") ? 443 : 80;
            }

            std::string full_url = scheme + "://" + host;
            if ((scheme == "https" && port != 443) || (scheme == "http" && port != 80)) {
                full_url += ":" + std::to_string(port);
            }

            httplib::Client cli(full_url);
            cli.set_connection_timeout(config_.timeout_ms / 1000, (config_.timeout_ms % 1000) * 1000);
            cli.set_read_timeout(config_.timeout_ms / 1000, (config_.timeout_ms % 1000) * 1000);

            httplib::Headers headers = {
                {"Authorization", "Bearer " + config_.api_key},
                {"Content-Type", "application/json"}
            };

            std::string endpoint = base_path + "/chat/completions";
            auto res = cli.Post(endpoint, headers, request_body.dump(), "application/json");

            if (!res) {
                return {
                    {"reply", "\xD0\x9E\xD1\x88\xD0\xB8\xD0\xB1\xD0\xBA\xD0\xB0: "
                              "\xD0\xBD\xD0\xB5 \xD1\x83\xD0\xB4\xD0\xB0\xD0\xBB\xD0\xBE\xD1\x81\xD1\x8C "
                              "\xD0\xBF\xD0\xBE\xD0\xB4\xD0\xBA\xD0\xBB\xD1\x8E\xD1\x87\xD0\xB8\xD1\x82\xD1\x8C\xD1\x81\xD1\x8F "
                              "\xD0\xBA API \xD0\xB0\xD1\x81\xD1\x81\xD0\xB8\xD1\x81\xD1\x82\xD0\xB5\xD0\xBD\xD1\x82\xD0\xB0."}, // "Ошибка: не удалось подключиться к API ассистента."
                    {"error", true}
                };
            }

            if (res->status != 200) {
                return {
                    {"reply", "\xD0\x9E\xD1\x88\xD0\xB8\xD0\xB1\xD0\xBA\xD0\xB0 API: HTTP " + std::to_string(res->status)}, // "Ошибка API: HTTP X"
                    {"error", true}
                };
            }

            auto response = nlohmann::json::parse(res->body);
            std::string reply = response["choices"][0]["message"]["content"].get<std::string>();

            // Get current timestamp
            std::string timestamp = get_current_timestamp();

            // Add to history
            history.push_back({"user", message, timestamp});
            history.push_back({"assistant", reply, timestamp});

            return {{"reply", reply}, {"timestamp", timestamp}, {"error", false}};

        } catch (const std::exception& e) {
            return {
                {"reply", std::string("\xD0\x9E\xD1\x88\xD0\xB8\xD0\xB1\xD0\xBA\xD0\xB0: ") + e.what()}, // "Ошибка: <msg>"
                {"error", true}
            };
        }
    }

    nlohmann::json get_status() {
        bool configured = !config_.api_key.empty() && config_.api_key != "sk-placeholder";
        return {{"configured", configured}, {"model", config_.model}};
    }

private:
    AssistantConfig config_;

    static std::string get_current_timestamp() {
        auto now = std::chrono::system_clock::now();
        auto time = std::chrono::system_clock::to_time_t(now);
        char buf[64];
        std::strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%S", std::localtime(&time));
        return std::string(buf);
    }
};
