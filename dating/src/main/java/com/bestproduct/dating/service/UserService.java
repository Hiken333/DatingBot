package com.bestproduct.dating.service;

import com.bestproduct.dating.config.AppConfig;
import com.bestproduct.dating.domain.entity.User;
import com.bestproduct.dating.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления пользователями
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final AppConfig appConfig;

    /**
     * Найти пользователя по Telegram ID
     */
    // @Cacheable(value = "users", key = "#telegramId", unless = "#result == null || !#result.isPresent()")
    public Optional<User> findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }

    /**
     * Найти пользователя по ID
     */
    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }

    /**
     * Создать нового пользователя
     */
    @Transactional
    public User createUser(Long telegramId, String firstName, String lastName, 
                          String username, LocalDate birthDate, User.Gender gender) {
        // Проверка возраста
        if (!isLegalAge(birthDate)) {
            throw new IllegalArgumentException("User must be at least " + 
                appConfig.getSecurity().getMinAge() + " years old");
        }

        // Проверка существования
        if (userRepository.existsByTelegramId(telegramId)) {
            throw new IllegalArgumentException("User with telegram ID " + telegramId + " already exists");
        }

        User user = User.builder()
            .telegramId(telegramId)
            .firstName(firstName)
            .lastName(lastName)
            .username(username)
            .birthDate(birthDate)
            .gender(gender)
            .status(User.UserStatus.PENDING_VERIFICATION)
            .role(User.UserRole.USER)
            .build();

        user = userRepository.save(user);
        log.info("Created new user: id={}, telegramId={}", user.getId(), telegramId);
        
        return user;
    }

    /**
     * Обновить последнюю активность пользователя
     */
    @Transactional
    // @CacheEvict(value = "users", key = "#userId")
    public void updateLastActive(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastActive(LocalDateTime.now());
            userRepository.save(user);
        });
    }

    /**
     * Заблокировать пользователя
     */
    @Transactional
    // @CacheEvict(value = "users", key = "#userId")
    public void banUser(Long userId, String reason) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setIsBanned(true);
        user.setBanReason(reason);
        user.setStatus(User.UserStatus.BANNED);
        userRepository.save(user);
        
        log.warn("User banned: id={}, reason={}", userId, reason);
    }

    /**
     * Разблокировать пользователя
     */
    @Transactional
    // @CacheEvict(value = "users", key = "#userId")
    public void unbanUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setIsBanned(false);
        user.setBanReason(null);
        user.setStatus(User.UserStatus.ACTIVE);
        userRepository.save(user);
        
        log.info("User unbanned: id={}", userId);
    }

    /**
     * Увеличить счетчик жалоб на пользователя
     */
    @Transactional
    // @CacheEvict(value = "users", key = "#userId")
    public void incrementReportCount(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setReportCount(user.getReportCount() + 1);
        userRepository.save(user);

        // Автоматический бан при превышении порога
        if (user.getReportCount() >= appConfig.getModeration().getAutoBanOnReports()) {
            banUser(userId, "Automatic ban: too many reports");
        }
    }

    /**
     * Активировать пользователя после верификации
     */
    @Transactional
    // @CacheEvict(value = "users", key = "#userId")
    public void activateUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setStatus(User.UserStatus.ACTIVE);
        userRepository.save(user);
        
        log.info("User activated: id={}", userId);
    }

    /**
     * Получить пользователей с высоким количеством жалоб
     */
    public List<User> getUsersWithHighReportCount() {
        return userRepository.findUsersWithHighReportCount(
            appConfig.getModeration().getAutoBanOnReports() - 1
        );
    }

    /**
     * Проверка достижения минимального возраста
     */
    private boolean isLegalAge(LocalDate birthDate) {
        int minAge = appConfig.getSecurity().getMinAge();
        LocalDate minBirthDate = LocalDate.now().minusYears(minAge);
        return birthDate.isBefore(minBirthDate) || birthDate.isEqual(minBirthDate);
    }

    /**
     * Получить всех заблокированных пользователей
     */
    public List<User> getBannedUsers() {
        return userRepository.findByIsBannedTrue();
    }

    /**
     * Получить статистику по пользователям
     */
    public UserStatistics getStatistics() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus(User.UserStatus.ACTIVE);
        long bannedUsers = userRepository.findByIsBannedTrue().size();
        
        return new UserStatistics(totalUsers, activeUsers, bannedUsers);
    }

    public record UserStatistics(long total, long active, long banned) {}
}



