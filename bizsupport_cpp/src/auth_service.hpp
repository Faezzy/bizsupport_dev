#pragma once
#include "repositories.hpp"
#include "jwt_utils.hpp"
#include "password_utils.hpp"
#include <nlohmann/json.hpp>
#include <stdexcept>

class AuthService {
public:
    AuthService(Repositories& repos, JwtUtils& jwt) : repos_(repos), jwt_(jwt) {}

    // Register a new user. Throws if email already exists.
    // Returns JSON: {token, email, fullName}
    nlohmann::json register_user(const std::string& email, const std::string& password, const std::string& full_name) {
        if (repos_.exists_by_email(email)) {
            throw std::runtime_error("Email already registered");
        }
        auto hash = password_utils::hash_password(password);
        auto user = repos_.save_user(email, hash, full_name);
        auto token = jwt_.generate_token(email);
        return {{"token", token}, {"email", user.email}, {"fullName", user.full_name}};
    }

    // Login. Throws if credentials invalid.
    // Returns JSON: {token, email, fullName}
    nlohmann::json login(const std::string& email, const std::string& password) {
        auto user_opt = repos_.find_user_by_email(email);
        if (!user_opt) throw std::runtime_error("Invalid credentials");
        if (!password_utils::verify_password(password, user_opt->password)) {
            throw std::runtime_error("Invalid credentials");
        }
        auto token = jwt_.generate_token(email);
        return {{"token", token}, {"email", user_opt->email}, {"fullName", user_opt->full_name}};
    }

    // Get current user from JWT token. Returns nullopt if invalid.
    std::optional<User> get_current_user(const std::string& token) {
        try {
            if (!jwt_.validate_token(token)) return std::nullopt;
            auto email = jwt_.get_email_from_token(token);
            return repos_.find_user_by_email(email);
        } catch (...) {
            return std::nullopt;
        }
    }

private:
    Repositories& repos_;
    JwtUtils& jwt_;
};
