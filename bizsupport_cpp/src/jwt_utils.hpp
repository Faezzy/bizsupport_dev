#pragma once
#include <string>
#include <openssl/hmac.h>
#include <openssl/bio.h>
#include <openssl/evp.h>
#include <openssl/buffer.h>
#include <nlohmann/json.hpp>
#include <chrono>
#include <ctime>
#include <stdexcept>
#include <vector>
#include <algorithm>
#include <cstring>
#include <sstream>

// Base64URL encode (no padding, URL-safe characters)
inline std::string base64url_encode(const std::string& input) {
    BIO* b64 = BIO_new(BIO_f_base64());
    BIO* bmem = BIO_new(BIO_s_mem());
    b64 = BIO_push(b64, bmem);

    BIO_set_flags(b64, BIO_FLAGS_BASE64_NO_NL);
    BIO_write(b64, input.data(), static_cast<int>(input.size()));
    BIO_flush(b64);

    BUF_MEM* bptr = nullptr;
    BIO_get_mem_ptr(b64, &bptr);

    std::string result(bptr->data, bptr->length);
    BIO_free_all(b64);

    // Convert to URL-safe: '+' -> '-', '/' -> '_', remove '='
    std::replace(result.begin(), result.end(), '+', '-');
    std::replace(result.begin(), result.end(), '/', '_');
    result.erase(std::remove(result.begin(), result.end(), '='), result.end());

    return result;
}

// Base64URL decode
inline std::string base64url_decode(const std::string& input) {
    // Convert from URL-safe back to standard base64
    std::string b64 = input;
    std::replace(b64.begin(), b64.end(), '-', '+');
    std::replace(b64.begin(), b64.end(), '_', '/');

    // Add padding
    switch (b64.size() % 4) {
        case 2: b64 += "=="; break;
        case 3: b64 += "=";  break;
        default: break;
    }

    BIO* bio = BIO_new_mem_buf(b64.data(), static_cast<int>(b64.size()));
    BIO* b64_filter = BIO_new(BIO_f_base64());
    bio = BIO_push(b64_filter, bio);
    BIO_set_flags(bio, BIO_FLAGS_BASE64_NO_NL);

    std::vector<char> buffer(b64.size());
    int decoded_len = BIO_read(bio, buffer.data(), static_cast<int>(buffer.size()));
    BIO_free_all(bio);

    if (decoded_len < 0) {
        throw std::runtime_error("Base64URL decode failed");
    }

    return std::string(buffer.data(), static_cast<size_t>(decoded_len));
}

// HMAC-SHA256 signature (returns raw bytes as a string)
inline std::string hmac_sha256(const std::string& key, const std::string& data) {
    unsigned char result[EVP_MAX_MD_SIZE];
    unsigned int result_len = 0;

    unsigned char* hmac_result = HMAC(
        EVP_sha256(),
        key.data(), static_cast<int>(key.size()),
        reinterpret_cast<const unsigned char*>(data.data()), data.size(),
        result, &result_len
    );

    if (!hmac_result) {
        throw std::runtime_error("HMAC-SHA256 computation failed");
    }

    return std::string(reinterpret_cast<char*>(result), result_len);
}

class JwtUtils {
public:
    JwtUtils(const std::string& secret, int expiration_seconds)
        : secret_(secret), expiration_seconds_(expiration_seconds) {
        if (secret.empty()) {
            throw std::invalid_argument("JWT secret must not be empty");
        }
        if (expiration_seconds <= 0) {
            throw std::invalid_argument("Expiration must be positive");
        }
    }

    // Generate JWT token for a given email
    std::string generate_token(const std::string& email) const {
        // Header
        nlohmann::json header;
        header["alg"] = "HS256";
        header["typ"] = "JWT";

        // Payload
        auto now = std::chrono::system_clock::now();
        auto iat = std::chrono::duration_cast<std::chrono::seconds>(
            now.time_since_epoch()).count();
        auto exp = iat + expiration_seconds_;

        nlohmann::json payload;
        payload["sub"] = email;
        payload["iat"] = iat;
        payload["exp"] = exp;

        // Encode header and payload
        std::string header_b64 = base64url_encode(header.dump());
        std::string payload_b64 = base64url_encode(payload.dump());

        // Create signing input
        std::string signing_input = header_b64 + "." + payload_b64;

        // Sign
        std::string signature = hmac_sha256(secret_, signing_input);
        std::string signature_b64 = base64url_encode(signature);

        return signing_input + "." + signature_b64;
    }

    // Extract email from token; throws if signature is invalid or token is malformed
    std::string get_email_from_token(const std::string& token) const {
        auto parts = split_token(token);

        // Verify signature
        std::string signing_input = parts[0] + "." + parts[1];
        std::string expected_sig = base64url_encode(hmac_sha256(secret_, signing_input));

        if (!constant_time_compare(parts[2], expected_sig)) {
            throw std::runtime_error("Invalid JWT signature");
        }

        // Decode payload
        std::string payload_json = base64url_decode(parts[1]);
        nlohmann::json payload = nlohmann::json::parse(payload_json);

        if (!payload.contains("sub") || !payload["sub"].is_string()) {
            throw std::runtime_error("JWT payload missing 'sub' field");
        }

        return payload["sub"].get<std::string>();
    }

    // Validate token: checks signature and expiration. Returns false on any error.
    bool validate_token(const std::string& token) const {
        try {
            auto parts = split_token(token);

            // Verify signature
            std::string signing_input = parts[0] + "." + parts[1];
            std::string expected_sig = base64url_encode(hmac_sha256(secret_, signing_input));

            if (!constant_time_compare(parts[2], expected_sig)) {
                return false;
            }

            // Decode payload and check expiration
            std::string payload_json = base64url_decode(parts[1]);
            nlohmann::json payload = nlohmann::json::parse(payload_json);

            if (!payload.contains("exp") || !payload["exp"].is_number()) {
                return false;
            }

            auto now = std::chrono::duration_cast<std::chrono::seconds>(
                std::chrono::system_clock::now().time_since_epoch()).count();

            return payload["exp"].get<int64_t>() > now;
        } catch (...) {
            return false;
        }
    }

private:
    std::string secret_;
    int expiration_seconds_;

    // Split a JWT token into its three parts; throws if malformed
    static std::vector<std::string> split_token(const std::string& token) {
        std::vector<std::string> parts;
        std::istringstream stream(token);
        std::string segment;
        while (std::getline(stream, segment, '.')) {
            parts.push_back(segment);
        }
        if (parts.size() != 3) {
            throw std::runtime_error("Malformed JWT: expected 3 parts, got "
                + std::to_string(parts.size()));
        }
        return parts;
    }

    // Constant-time string comparison to mitigate timing attacks
    static bool constant_time_compare(const std::string& a, const std::string& b) {
        if (a.size() != b.size()) {
            return false;
        }
        volatile unsigned char result = 0;
        for (size_t i = 0; i < a.size(); ++i) {
            result |= static_cast<unsigned char>(a[i]) ^ static_cast<unsigned char>(b[i]);
        }
        return result == 0;
    }
};
