#pragma once
#include "repositories.hpp"
#include <string>
#include <vector>

class LegalReferenceService {
public:
    LegalReferenceService(Repositories& repos) : repos_(repos) {}

    // Get legal references for a specific entity
    std::vector<LegalReference> get_references(const std::string& entity_type, int64_t entity_id) {
        return repos_.find_references(entity_type, entity_id);
    }

    // Get all legal references of a given entity type
    std::vector<LegalReference> get_all_by_type(const std::string& entity_type) {
        return repos_.find_references_by_type(entity_type);
    }

private:
    Repositories& repos_;
};
