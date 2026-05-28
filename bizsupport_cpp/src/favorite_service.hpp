#pragma once
#include "repositories.hpp"
#include <string>
#include <vector>

class FavoriteService {
public:
    FavoriteService(Repositories& repos) : repos_(repos) {}

    // Get all favorites for a user
    std::vector<UserFavorite> get_user_favorites(int64_t user_id) {
        return repos_.find_user_favorites(user_id);
    }

    // Get favorites for a user filtered by entity type
    std::vector<UserFavorite> get_by_type(int64_t user_id, const std::string& entity_type) {
        return repos_.find_user_favorites_by_type(user_id, entity_type);
    }

    // Check if a specific entity is favorited by a user
    bool is_favorite(int64_t user_id, const std::string& entity_type, int64_t entity_id) {
        return repos_.is_favorite(user_id, entity_type, entity_id);
    }

    // Toggle favorite status. Returns true if added, false if removed.
    bool toggle(int64_t user_id, const std::string& entity_type, int64_t entity_id) {
        if (repos_.is_favorite(user_id, entity_type, entity_id)) {
            repos_.remove_favorite(user_id, entity_type, entity_id);
            return false;
        } else {
            repos_.add_favorite(user_id, entity_type, entity_id);
            return true;
        }
    }

    // Remove a favorite by its ID
    void remove(int64_t id) {
        repos_.remove_favorite_by_id(id);
    }

private:
    Repositories& repos_;
};
