#pragma once
#include <pqxx/pqxx>
#include <string>
#include <queue>
#include <mutex>
#include <memory>
#include <condition_variable>
#include <stdexcept>
#include <chrono>

class ConnectionPool {
public:
    ConnectionPool(const std::string& connection_string, int pool_size = 5)
        : conn_string_(connection_string), max_size_(pool_size) {
        if (pool_size <= 0) {
            throw std::invalid_argument("pool_size must be positive");
        }
        for (int i = 0; i < pool_size; ++i) {
            pool_.push(std::make_shared<pqxx::connection>(connection_string));
        }
    }

    ~ConnectionPool() = default;

    // Non-copyable, non-movable
    ConnectionPool(const ConnectionPool&) = delete;
    ConnectionPool& operator=(const ConnectionPool&) = delete;
    ConnectionPool(ConnectionPool&&) = delete;
    ConnectionPool& operator=(ConnectionPool&&) = delete;

    std::shared_ptr<pqxx::connection> acquire() {
        std::unique_lock<std::mutex> lock(mutex_);
        cv_.wait(lock, [this] { return !pool_.empty(); });
        auto conn = pool_.front();
        pool_.pop();
        // Reconnect if the connection was dropped
        if (!conn->is_open()) {
            conn = std::make_shared<pqxx::connection>(conn_string_);
        }
        return conn;
    }

    // Timed acquire -- returns nullptr on timeout
    std::shared_ptr<pqxx::connection> acquire(std::chrono::milliseconds timeout) {
        std::unique_lock<std::mutex> lock(mutex_);
        if (!cv_.wait_for(lock, timeout, [this] { return !pool_.empty(); })) {
            return nullptr;
        }
        auto conn = pool_.front();
        pool_.pop();
        if (!conn->is_open()) {
            conn = std::make_shared<pqxx::connection>(conn_string_);
        }
        return conn;
    }

    void release(std::shared_ptr<pqxx::connection> conn) {
        if (!conn) return;
        std::unique_lock<std::mutex> lock(mutex_);
        pool_.push(std::move(conn));
        cv_.notify_one();
    }

    int available() const {
        std::unique_lock<std::mutex> lock(mutex_);
        return static_cast<int>(pool_.size());
    }

    int max_size() const { return max_size_; }

    // RAII guard -- automatically returns the connection to the pool on destruction
    class ConnectionGuard {
    public:
        explicit ConnectionGuard(ConnectionPool& pool)
            : pool_(&pool), conn_(pool.acquire()) {}

        // Timed constructor -- throws on timeout
        ConnectionGuard(ConnectionPool& pool, std::chrono::milliseconds timeout)
            : pool_(&pool), conn_(pool.acquire(timeout)) {
            if (!conn_) {
                throw std::runtime_error("Timed out waiting for database connection");
            }
        }

        ~ConnectionGuard() {
            if (conn_ && pool_) {
                try {
                    pool_->release(std::move(conn_));
                } catch (...) {
                    // Suppress exceptions in destructor
                }
            }
        }

        // Move-only
        ConnectionGuard(ConnectionGuard&& other) noexcept
            : pool_(other.pool_), conn_(std::move(other.conn_)) {
            other.pool_ = nullptr;
            other.conn_ = nullptr;
        }

        ConnectionGuard& operator=(ConnectionGuard&& other) noexcept {
            if (this != &other) {
                // Release current connection first
                if (conn_ && pool_) {
                    try {
                        pool_->release(std::move(conn_));
                    } catch (...) {}
                }
                pool_ = other.pool_;
                conn_ = std::move(other.conn_);
                other.pool_ = nullptr;
                other.conn_ = nullptr;
            }
            return *this;
        }

        ConnectionGuard(const ConnectionGuard&) = delete;
        ConnectionGuard& operator=(const ConnectionGuard&) = delete;

        pqxx::connection& get() { return *conn_; }
        const pqxx::connection& get() const { return *conn_; }
        pqxx::connection* operator->() { return conn_.get(); }
        const pqxx::connection* operator->() const { return conn_.get(); }

    private:
        ConnectionPool* pool_;
        std::shared_ptr<pqxx::connection> conn_;
    };

private:
    std::string conn_string_;
    int max_size_;
    std::queue<std::shared_ptr<pqxx::connection>> pool_;
    mutable std::mutex mutex_;
    std::condition_variable cv_;
};

// Helper to build connection string from config
inline std::string build_connection_string(const std::string& host, int port,
    const std::string& dbname, const std::string& user, const std::string& password) {
    return "host=" + host + " port=" + std::to_string(port) +
           " dbname=" + dbname + " user=" + user + " password=" + password;
}
