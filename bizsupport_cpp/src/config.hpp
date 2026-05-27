#pragma once

#include <string>
#include <fstream>
#include <sstream>
#include <stdexcept>
#include <map>

struct DatabaseConfig {
    std::string host = "localhost";
    int         port = 5432;
    std::string dbname;
    std::string user;
    std::string password;
};

struct ServerConfig {
    int port = 8080;
};

struct JwtConfig {
    std::string secret;
    int         expiration_seconds = 86400;
};

struct AssistantConfig {
    std::string base_url;
    std::string api_key;
    std::string model;
    int         max_tokens    = 1000;
    double      temperature   = 0.7;
    int         timeout_ms    = 30000;
};

struct AppConfig {
    DatabaseConfig  database;
    ServerConfig    server;
    JwtConfig       jwt;
    AssistantConfig assistant;

    /// Parse an INI-style configuration file and return a populated AppConfig.
    static AppConfig load(const std::string& filepath) {
        std::ifstream file(filepath);
        if (!file.is_open()) {
            throw std::runtime_error("Cannot open config file: " + filepath);
        }

        AppConfig cfg;
        std::string current_section;
        std::string line;

        while (std::getline(file, line)) {
            // Trim leading / trailing whitespace
            auto ltrim = line.find_first_not_of(" \t\r\n");
            if (ltrim == std::string::npos) continue;
            line = line.substr(ltrim);
            auto rtrim = line.find_last_not_of(" \t\r\n");
            if (rtrim != std::string::npos) {
                line = line.substr(0, rtrim + 1);
            }

            // Skip empty lines and comments
            if (line.empty() || line[0] == '#' || line[0] == ';') continue;

            // Section header
            if (line.front() == '[' && line.back() == ']') {
                current_section = line.substr(1, line.size() - 2);
                continue;
            }

            // key=value
            auto eq = line.find('=');
            if (eq == std::string::npos) continue;

            std::string key   = line.substr(0, eq);
            std::string value = line.substr(eq + 1);

            // Trim key
            auto kt = key.find_last_not_of(" \t");
            if (kt != std::string::npos) key = key.substr(0, kt + 1);

            // Trim value
            auto vt = value.find_first_not_of(" \t");
            if (vt != std::string::npos) value = value.substr(vt);
            auto vt2 = value.find_last_not_of(" \t\r\n");
            if (vt2 != std::string::npos) value = value.substr(0, vt2 + 1);

            // Assign to the correct config struct
            if (current_section == "database") {
                if      (key == "host")     cfg.database.host     = value;
                else if (key == "port")     cfg.database.port     = std::stoi(value);
                else if (key == "dbname")   cfg.database.dbname   = value;
                else if (key == "user")     cfg.database.user     = value;
                else if (key == "password") cfg.database.password = value;
            } else if (current_section == "server") {
                if (key == "port") cfg.server.port = std::stoi(value);
            } else if (current_section == "jwt") {
                if      (key == "secret")             cfg.jwt.secret             = value;
                else if (key == "expiration_seconds") cfg.jwt.expiration_seconds = std::stoi(value);
            } else if (current_section == "assistant") {
                if      (key == "base_url")    cfg.assistant.base_url    = value;
                else if (key == "api_key")     cfg.assistant.api_key     = value;
                else if (key == "model")       cfg.assistant.model       = value;
                else if (key == "max_tokens")  cfg.assistant.max_tokens  = std::stoi(value);
                else if (key == "temperature") cfg.assistant.temperature = std::stod(value);
                else if (key == "timeout_ms")  cfg.assistant.timeout_ms  = std::stoi(value);
            }
        }

        return cfg;
    }
};
