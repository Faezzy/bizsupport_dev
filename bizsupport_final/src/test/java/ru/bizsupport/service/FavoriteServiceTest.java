package ru.bizsupport.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.bizsupport.entity.*;
import ru.bizsupport.repository.UserFavoriteRepository;
import ru.bizsupport.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FavoriteService — юнит-тесты")
class FavoriteServiceTest {

    @Mock private UserFavoriteRepository favoriteRepo;
    @Mock private UserRepository userRepo;
    @InjectMocks private FavoriteService favoriteService;

    @Test
    @DisplayName("isFavorite — возвращает true если существует")
    void isFavoriteTrue() {
        when(favoriteRepo.existsByUserIdAndEntityTypeAndEntityId(1L, EntityType.TAX_REGIME, 1L))
                .thenReturn(true);
        assertThat(favoriteService.isFavorite(1L, EntityType.TAX_REGIME, 1L)).isTrue();
    }

    @Test
    @DisplayName("isFavorite — возвращает false если не существует")
    void isFavoriteFalse() {
        when(favoriteRepo.existsByUserIdAndEntityTypeAndEntityId(1L, EntityType.TAX_REGIME, 99L))
                .thenReturn(false);
        assertThat(favoriteService.isFavorite(1L, EntityType.TAX_REGIME, 99L)).isFalse();
    }

    @Test
    @DisplayName("toggle — добавляет если не было, возвращает true")
    void toggleAdds() {
        when(favoriteRepo.findByUserIdAndEntityTypeAndEntityId(1L, EntityType.PROCUREMENT, 1L))
                .thenReturn(Optional.empty());
        when(userRepo.findById(1L)).thenReturn(Optional.of(
                User.builder().id(1L).email("test@test.ru").build()));

        boolean result = favoriteService.toggle(1L, EntityType.PROCUREMENT, 1L);
        assertThat(result).isTrue();
        verify(favoriteRepo).save(any(UserFavorite.class));
    }

    @Test
    @DisplayName("toggle — удаляет если уже было, возвращает false")
    void toggleRemoves() {
        UserFavorite existing = UserFavorite.builder().id(10L).build();
        when(favoriteRepo.findByUserIdAndEntityTypeAndEntityId(1L, EntityType.TAX_REGIME, 1L))
                .thenReturn(Optional.of(existing));

        boolean result = favoriteService.toggle(1L, EntityType.TAX_REGIME, 1L);
        assertThat(result).isFalse();
        verify(favoriteRepo).delete(existing);
    }

    @Test
    @DisplayName("getUserFavorites — делегирует в репозиторий")
    void getUserFavorites() {
        when(favoriteRepo.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        List<UserFavorite> result = favoriteService.getUserFavorites(1L);
        assertThat(result).isEmpty();
        verify(favoriteRepo).findByUserIdOrderByCreatedAtDesc(1L);
    }
}
