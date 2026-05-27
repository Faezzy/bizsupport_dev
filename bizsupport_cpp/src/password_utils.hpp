#pragma once
#include <string>
#include <openssl/evp.h>
#include <openssl/rand.h>
#include <sstream>
#include <iomanip>
#include <vector>
#include <stdexcept>
#include <cstring>

namespace password_utils {

// Convert raw bytes to a lowercase hex string
inline std::string bytes_to_hex(const unsigned char* data, size_t len) {
    std::ostringstream oss;
    oss << std::hex << std::setfill('0');
    for (size_t i = 0; i < len; ++i) {
        oss << std::setw(2) << static_cast<unsigned int>(data[i]);
    }
    return oss.str();
}

// Convert a hex string to raw bytes. Returns the number of bytes written.
inline size_t hex_to_bytes(const std::string& hex, unsigned char* out, size_t max_len) {
    if (hex.size() % 2 != 0) {
        throw std::invalid_argument("Hex string length must be even");
    }
    size_t byte_count = hex.size() / 2;
    if (byte_count > max_len) {
        throw std::invalid_argument("Output buffer too small for hex string");
    }
    for (size_t i = 0; i < byte_count; ++i) {
        unsigned int byte_val = 0;
        std::istringstream iss(hex.substr(i * 2, 2));
        iss >> std::hex >> byte_val;
        if (iss.fail()) {
            throw std::invalid_argument("Invalid hex character in string");
        }
        out[i] = static_cast<unsigned char>(byte_val);
    }
    return byte_count;
}

constexpr int SALT_LENGTH = 16;        // 16 bytes
constexpr int HASH_LENGTH = 32;        // 32 bytes (SHA-256 output)
constexpr int ITERATIONS = 10000;
static const std::string HASH_PREFIX = "$pbkdf2$";

// Hash a password using PBKDF2-SHA256 with a random salt.
// Returns a string in the format: $pbkdf2$<salt_hex>$<hash_hex>
inline std::string hash_password(const std::string& password) {
    // Generate random salt
    unsigned char salt[SALT_LENGTH];
    if (RAND_bytes(salt, SALT_LENGTH) != 1) {
        throw std::runtime_error("Failed to generate random salt");
    }

    // Derive key using PBKDF2-SHA256
    unsigned char hash[HASH_LENGTH];
    if (PKCS5_PBKDF2_HMAC(
            password.c_str(), static_cast<int>(password.size()),
            salt, SALT_LENGTH,
            ITERATIONS,
            EVP_sha256(),
            HASH_LENGTH, hash) != 1) {
        throw std::runtime_error("PBKDF2 key derivation failed");
    }

    std::string salt_hex = bytes_to_hex(salt, SALT_LENGTH);
    std::string hash_hex = bytes_to_hex(hash, HASH_LENGTH);

    return HASH_PREFIX + salt_hex + "$" + hash_hex;
}

// Verify a password against a stored hash string.
// The hash must be in the format: $pbkdf2$<salt_hex>$<hash_hex>
inline bool verify_password(const std::string& password, const std::string& hash) {
    // Check prefix
    if (hash.substr(0, HASH_PREFIX.size()) != HASH_PREFIX) {
        return false;
    }

    // Strip prefix and split into salt and hash parts
    std::string remainder = hash.substr(HASH_PREFIX.size());
    size_t sep = remainder.find('$');
    if (sep == std::string::npos) {
        return false;
    }

    std::string salt_hex = remainder.substr(0, sep);
    std::string stored_hash_hex = remainder.substr(sep + 1);

    // Decode salt
    unsigned char salt[SALT_LENGTH];
    size_t salt_len = 0;
    try {
        salt_len = hex_to_bytes(salt_hex, salt, SALT_LENGTH);
    } catch (...) {
        return false;
    }

    if (salt_len != SALT_LENGTH) {
        return false;
    }

    // Recompute hash with the stored salt
    unsigned char computed_hash[HASH_LENGTH];
    if (PKCS5_PBKDF2_HMAC(
            password.c_str(), static_cast<int>(password.size()),
            salt, static_cast<int>(salt_len),
            ITERATIONS,
            EVP_sha256(),
            HASH_LENGTH, computed_hash) != 1) {
        return false;
    }

    std::string computed_hex = bytes_to_hex(computed_hash, HASH_LENGTH);

    // Constant-time comparison to prevent timing attacks
    if (computed_hex.size() != stored_hash_hex.size()) {
        return false;
    }
    volatile unsigned char diff = 0;
    for (size_t i = 0; i < computed_hex.size(); ++i) {
        diff |= static_cast<unsigned char>(computed_hex[i])
              ^ static_cast<unsigned char>(stored_hash_hex[i]);
    }
    return diff == 0;
}

} // namespace password_utils
