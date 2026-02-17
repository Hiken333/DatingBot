package com.bestproduct.dating.service;

import com.bestproduct.dating.config.AppConfig;
import com.bestproduct.dating.domain.entity.Profile;
import com.bestproduct.dating.domain.entity.User;
import com.bestproduct.dating.repository.ProfileRepository;
import com.bestproduct.dating.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Сервис для управления профилями пользователей
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final AppConfig appConfig;
    
    private static final GeometryFactory geometryFactory = 
        new GeometryFactory(new PrecisionModel(), 4326);

    /**
     * Получить профиль по ID пользователя
     */
    public Optional<Profile> getProfileByUserId(Long userId) {
        return profileRepository.findByUserId(userId);
    }

    /**
     * Создать профиль для пользователя
     */
    @Transactional
    // @CacheEvict(value = "profiles", key = "#userId")
    public Profile createProfile(Long userId, String bio, List<String> photoUrls,
                                 double latitude, double longitude) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Проверка существования профиля
        if (profileRepository.findByUserId(userId).isPresent()) {
            throw new IllegalArgumentException("Profile already exists for user");
        }

        Point location = geometryFactory.createPoint(new Coordinate(longitude, latitude));

        Profile profile = Profile.builder()
            .user(user)
            .bio(bio)
            .photoUrls(photoUrls)
            .location(location)
            .locationUpdatedAt(LocalDateTime.now())
            .maxDistanceKm(appConfig.getGeo().getDefaultSearchRadiusKm())
            .isVisible(true)
            .profileCompleted(false)
            .build();

        profile = profileRepository.save(profile);
        log.info("Created profile for user: userId={}", userId);
        
        return profile;
    }

    /**
     * Обновить локацию профиля
     */
    @Transactional
    // @CacheEvict(value = "profiles", key = "#userId")
    public void updateLocation(Long userId, double latitude, double longitude, 
                              String city, String country) {
        Profile profile = profileRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        Point location = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        profile.setLocation(location);
        profile.setCity(city);
        profile.setCountry(country);
        profile.setLocationUpdatedAt(LocalDateTime.now());
        
        profileRepository.save(profile);
        log.debug("Updated location for user: userId={}", userId);
    }

    /**
     * Обновить фотографии профиля
     */
    @Transactional
    // @CacheEvict(value = "profiles", key = "#userId")
    public void updatePhotos(Long userId, List<String> photoUrls) {
        Profile profile = profileRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        if (photoUrls.size() > appConfig.getImages().getMaxPerProfile()) {
            throw new IllegalArgumentException("Too many photos. Maximum: " + 
                appConfig.getImages().getMaxPerProfile());
        }

        profile.setPhotoUrls(photoUrls);
        profileRepository.save(profile);
        log.debug("Updated photos for user: userId={}", userId);
    }

    /**
     * Обновить био профиля
     */
    @Transactional
    // @CacheEvict(value = "profiles", key = "#userId")
    public void updateBio(Long userId, String bio) {
        Profile profile = profileRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        profile.setBio(bio);
        profileRepository.save(profile);
    }

    /**
     * Установить предпочтения по алкоголю
     */
    @Transactional
    // @CacheEvict(value = "profiles", key = "#userId")
    public void setAlcoholPreferences(Long userId, List<Profile.AlcoholPreference> preferences) {
        Profile profile = profileRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        profile.setAlcoholPreferences(preferences);
        profileRepository.save(profile);
    }

    /**
     * Установить частоту употребления алкоголя
     */
    @Transactional
    // @CacheEvict(value = "profiles", key = "#userId")
    public void setDrinkingFrequency(Long userId, Profile.DrinkingFrequency frequency) {
        Profile profile = profileRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        profile.setDrinkingFrequency(frequency);
        profileRepository.save(profile);
    }

    /**
     * Завершить заполнение профиля
     */
    @Transactional
    // @CacheEvict(value = "profiles", key = "#userId")
    public void completeProfile(Long userId) {
        Profile profile = profileRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        if (!profile.isComplete()) {
            throw new IllegalArgumentException("Profile is not fully filled out");
        }

        profile.setProfileCompleted(true);
        profileRepository.save(profile);
        log.info("Profile completed for user: userId={}", userId);
    }

    /**
     * Переключить видимость профиля
     */
    @Transactional
    // @CacheEvict(value = "profiles", key = "#userId")
    public void toggleVisibility(Long userId) {
        Profile profile = profileRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        profile.setIsVisible(!profile.getIsVisible());
        profileRepository.save(profile);
    }

    /**
     * Найти профили поблизости (асинхронно)
     * Вся работа с БД выполняется внутри транзакции, затем результат возвращается в CompletableFuture
     */
    @Async("backgroundTaskExecutor")
    @Transactional(readOnly = true)
    public CompletableFuture<List<Profile>> findNearbyProfilesAsync(Long userId, int radiusKm, int limit) {
        try {
            Profile userProfile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

            if (userProfile.getLocation() == null) {
                throw new IllegalArgumentException("User location not set");
            }

            // Убрали ограничения по радиусу - показываем всех пользователей от ближайшего к дальнему
            // Найти профили, исключая уже оцененных (SQL сам вычисляет даты)
            List<Profile> nearbyProfiles = profileRepository.findNearbyProfiles(
                userId, 
                userProfile.getLocation(), 
                limit
            );
            
            // Загрузить User для каждого профиля
            if (!nearbyProfiles.isEmpty()) {
                List<Long> profileIds = nearbyProfiles.stream().map(Profile::getId).toList();
                List<Profile> profilesWithUser = profileRepository.findByIdsWithUser(profileIds);
                
                // Инициализировать и скопировать коллекции photoUrls внутри транзакции
                // Это необходимо, чтобы избежать LazyInitializationException в асинхронных колбэках
                // Копируем данные в новый ArrayList, чтобы они были доступны после закрытия транзакции
                for (Profile profile : profilesWithUser) {
                    if (profile.getPhotoUrls() != null) {
                        // Загрузить коллекцию и скопировать в новый список
                        List<String> photoUrlsCopy = new ArrayList<>(profile.getPhotoUrls());
                        profile.setPhotoUrls(photoUrlsCopy);
                    }
                }
                
                // Вернуть результат в CompletableFuture после завершения транзакции
                return CompletableFuture.completedFuture(profilesWithUser);
            }
            
            return CompletableFuture.completedFuture(nearbyProfiles);
        } catch (Exception e) {
            log.error("Error finding nearby profiles for user {}", userId, e);
            CompletableFuture<List<Profile>> future = new CompletableFuture<>();
            future.completeExceptionally(new CompletionException(e));
            return future;
        }
    }

    /**
     * Найти профили поблизости (синхронная версия для обратной совместимости)
     * @deprecated Используйте findNearbyProfilesAsync для лучшей производительности
     */
    @Deprecated
    @Transactional(readOnly = true)
    public List<Profile> findNearbyProfiles(Long userId, int radiusKm, int limit) {
        try {
            return findNearbyProfilesAsync(userId, radiusKm, limit).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e.getCause();
            }
            throw new RuntimeException("Error finding nearby profiles", e.getCause());
        }
    }

    /**
     * Обновить рейтинг профиля
     */
    @Transactional
    // @CacheEvict(value = "profiles", key = "#userId")
    public void updateRating(Long userId, double newRating) {
        Profile profile = profileRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        profile.updateRating(newRating);
        profileRepository.save(profile);
    }

    /**
     * Создать Point из координат
     */
    public Point createPoint(double latitude, double longitude) {
        return geometryFactory.createPoint(new Coordinate(longitude, latitude));
    }
}



