package ru.bizsupport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bizsupport.entity.*;
import ru.bizsupport.repository.UserFavoriteRepository;
import ru.bizsupport.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final UserFavoriteRepository favoriteRepo;
    private final UserRepository userRepo;

    /** Все избранные пользователя */
    public List<UserFavorite> getUserFavorites(Long userId) {
        return favoriteRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** Избранные по типу */
    public List<UserFavorite> getUserFavoritesByType(Long userId, EntityType type) {
        return favoriteRepo.findByUserIdAndEntityType(userId, type);
    }

    /** Проверка: в избранном ли? */
    public boolean isFavorite(Long userId, EntityType type, Long entityId) {
        return favoriteRepo.existsByUserIdAndEntityTypeAndEntityId(userId, type, entityId);
    }

    /**
     * Переключить избранное: если есть — удалить, нет — добавить.
     * @return true если добавлено, false если удалено
     */
    @Transactional
    public boolean toggle(Long userId, EntityType type, Long entityId) {
        var existing = favoriteRepo.findByUserIdAndEntityTypeAndEntityId(userId, type, entityId);
        if (existing.isPresent()) {
            favoriteRepo.delete(existing.get());
            return false;
        } else {
            User user = userRepo.findById(userId).orElseThrow();
            UserFavorite fav = UserFavorite.builder()
                    .user(user)
                    .entityType(type)
                    .entityId(entityId)
                    .build();
            favoriteRepo.save(fav);
            return true;
        }
    }

    /** Удалить из избранного */
    @Transactional
    public void remove(Long userId, EntityType type, Long entityId) {
        favoriteRepo.findByUserIdAndEntityTypeAndEntityId(userId, type, entityId)
                .ifPresent(favoriteRepo::delete);
    }
}
