package ru.bizsupport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.bizsupport.entity.EntityType;
import ru.bizsupport.entity.UserFavorite;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long> {

    List<UserFavorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<UserFavorite> findByUserIdAndEntityType(Long userId, EntityType entityType);

    /** Все избранные данного типа по всем пользователям (для генерации уведомлений) */
    List<UserFavorite> findByEntityType(EntityType entityType);

    Optional<UserFavorite> findByUserIdAndEntityTypeAndEntityId(Long userId, EntityType entityType, Long entityId);

    boolean existsByUserIdAndEntityTypeAndEntityId(Long userId, EntityType entityType, Long entityId);

    void deleteByUserIdAndEntityTypeAndEntityId(Long userId, EntityType entityType, Long entityId);
}
