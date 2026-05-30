#pragma once
#include "repositories.hpp"
#include <string>
#include <vector>
#include <chrono>
#include <ctime>
#include <sstream>
#include <algorithm>

class NotificationService {
public:
    NotificationService(Repositories& repos) : repos_(repos) {}

    // Get all notifications for a user (ordered by creation time desc)
    std::vector<Notification> get_user_notifications(int64_t user_id) {
        return repos_.find_user_notifications(user_id);
    }

    // Get only unread notifications for a user
    std::vector<Notification> get_unread(int64_t user_id) {
        return repos_.find_unread_notifications(user_id);
    }

    // Count unread notifications for a user
    int64_t count_unread(int64_t user_id) {
        return repos_.count_unread(user_id);
    }

    // Mark a single notification as read
    void mark_as_read(int64_t id) {
        repos_.mark_as_read(id);
    }

    // Mark all notifications as read for a user
    void mark_all_as_read(int64_t user_id) {
        repos_.mark_all_as_read(user_id);
    }

    // Delete a notification
    void delete_notification(int64_t id) {
        repos_.delete_notification(id);
    }

    // Generate reminder notifications for upcoming deadlines.
    // For every user with a company profile, scans company deadlines plus
    // template deadlines of their current tax regimes; creates a reminder when
    // a deadline is 7, 3 or 1 days away. Skips duplicates within a 12-hour window.
    void generate_reminders() {
        auto users = repos_.find_all_users();
        for (const auto& user : users) {
            auto profile_opt = repos_.find_profile_by_user_id(user.id);
            if (!profile_opt) continue;
            int64_t company_id = profile_opt->id;

            std::vector<Deadline> deadlines = repos_.find_company_deadlines(company_id);

            // Add template deadlines for the company's current regimes
            auto regimes = repos_.find_current_regimes(company_id);
            for (const auto& ctr : regimes) {
                auto templates = repos_.find_template_deadlines(ctr.tax_regime_id);
                for (const auto& td : templates) {
                    deadlines.push_back(td);
                }
            }

            for (const auto& deadline : deadlines) {
                int days_left = days_until(deadline.due_date);
                if (days_left == 7 || days_left == 3 || days_left == 1) {
                    if (!repos_.notification_exists_recent(user.id, deadline.id, 12)) {
                        create_reminder(user.id, deadline, days_left);
                    }
                }
            }
        }
    }

private:
    Repositories& repos_;

    // Days from today until a "YYYY-MM-DD" date string. Negative if past.
    static int days_until(const std::string& due_date) {
        if (due_date.size() < 10) return -1;
        struct tm due{};
        try {
            due.tm_year = std::stoi(due_date.substr(0, 4)) - 1900;
            due.tm_mon  = std::stoi(due_date.substr(5, 2)) - 1;
            due.tm_mday = std::stoi(due_date.substr(8, 2));
        } catch (...) {
            return -1;
        }
        due.tm_hour = 12;
        std::time_t due_t = mktime(&due);

        std::time_t now = std::time(nullptr);
        struct tm today{};
        localtime_r(&now, &today);
        today.tm_hour = 12; today.tm_min = 0; today.tm_sec = 0;
        std::time_t today_t = mktime(&today);

        return static_cast<int>((due_t - today_t) / 86400);
    }

    void create_reminder(int64_t user_id, const Deadline& deadline, int days_before) {
        std::string urgency;
        if (days_before == 1) {
            urgency = "Срочно! Завтра";
        } else {
            urgency = "Через " + std::to_string(days_before) + " дн.";
        }

        std::string title = urgency + ": " + deadline.title;
        std::string message = deadline.description.empty()
            ? ("Дедлайн: " + deadline.due_date)
            : deadline.description;

        repos_.save_notification(user_id, deadline.id, title, message);
    }
};
