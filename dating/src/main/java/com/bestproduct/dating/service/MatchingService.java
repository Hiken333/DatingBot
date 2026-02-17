package com.bestproduct.dating.service;

import com.bestproduct.dating.config.AppConfig;
import com.bestproduct.dating.domain.entity.Like;
import com.bestproduct.dating.domain.entity.Match;
import com.bestproduct.dating.domain.entity.User;
import com.bestproduct.dating.repository.LikeRepository;
import com.bestproduct.dating.repository.MatchRepository;
import com.bestproduct.dating.repository.SwipeHistoryRepository;
import com.bestproduct.dating.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления лайками и мэтчами
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingService {

    private final LikeRepository likeRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final SwipeHistoryRepository swipeHistoryRepository;
    private final NotificationService notificationService;
    private final TelegramNotificationService telegramNotificationService;
    private final AppConfig appConfig;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DistributedLockService lockService;

    private static final Duration SWIPE_CACHE_TTL = Duration.ofDays(7);
    private static final Duration LIKE_CACHE_TTL = Duration.ofDays(30);
    private static final Duration STATS_CACHE_TTL = Duration.ofMinutes(5);

    /**
     * Поставить лайк пользователю (с распределенной блокировкой)
     */
    @Transactional
    public LikeResult likeUser(Long fromUserId, Long toUserId, String message, boolean isSuperLike) {
        // Проверка лимита лайков
        long todayLikes = getDailyLikeCount(fromUserId);
        
        if (todayLikes >= appConfig.getMatching().getMaxDailyLikes()) {
            throw new IllegalArgumentException("Daily like limit reached");
        }

        // Проверка существования свайпа (лайк, дизлайк или суперлайк)
        if (hasSwipeRecord(fromUserId, toUserId)) {
            throw new IllegalArgumentException("Already swiped this user");
        }

        // Используем distributed lock для предотвращения race condition при создании матча
        String lockKey = DistributedLockService.matchLockKey(fromUserId, toUserId);
        
        return lockService.executeWithLock(lockKey, () -> {
            return doLikeUser(fromUserId, toUserId, message, isSuperLike);
        });
    }

    /**
     * Внутренняя логика лайка (выполняется под блокировкой)
     */
    private LikeResult doLikeUser(Long fromUserId, Long toUserId, String message, boolean isSuperLike) {
        // Еще раз проверяем свайп внутри блокировки
        if (hasSwipeRecord(fromUserId, toUserId)) {
            throw new IllegalArgumentException("Already swiped this user");
        }

        User fromUser = userRepository.findById(fromUserId)
            .orElseThrow(() -> new IllegalArgumentException("From user not found"));
        User toUser = userRepository.findById(toUserId)
            .orElseThrow(() -> new IllegalArgumentException("To user not found"));

        // Создание лайка
        Like like = Like.builder()
            .fromUser(fromUser)
            .toUser(toUser)
            .message(message)
            .isSuperLike(isSuperLike)
            .build();

        likeRepository.save(like);
        cacheUserLike(fromUserId, toUserId);
        incrementDailyLikeCount(fromUserId);
        
        // Сохранить в историю свайпов
        saveSwipeHistory(fromUserId, toUserId, isSuperLike ? 
            com.bestproduct.dating.domain.entity.SwipeHistory.SwipeType.SUPER_LIKE : 
            com.bestproduct.dating.domain.entity.SwipeHistory.SwipeType.LIKE);
        
        log.info("User {} liked user {}", fromUserId, toUserId);
        invalidateStatisticsCache(fromUserId);
        invalidateStatisticsCache(toUserId);

        // Проверка взаимного лайка
        if (hasLike(toUserId, fromUserId)) {
            // Создание мэтча (уже под блокировкой, поэтому безопасно)
            Match match = createMatchIfNotExists(fromUser, toUser);
            invalidateStatisticsCache(fromUserId);
            invalidateStatisticsCache(toUserId);
            
            // Отправка уведомлений асинхронно
            sendMatchNotificationsAsync(fromUserId, toUserId, match.getId());
            
            log.info("Match created between users {} and {}", fromUserId, toUserId);
            return new LikeResult(true, true, match.getId());
        }

        // Уведомление о новом лайке асинхронно
        sendLikeNotificationsAsync(fromUserId, toUserId, isSuperLike);

        return new LikeResult(true, false, null);
    }

    /**
     * Отправка уведомлений о матче асинхронно
     */
    private void sendMatchNotificationsAsync(Long fromUserId, Long toUserId, Long matchId) {
        // Используем @Async методы для отправки уведомлений
        try {
            notificationService.sendMatchNotification(fromUserId, toUserId, matchId);
            notificationService.sendMatchNotification(toUserId, fromUserId, matchId);
            telegramNotificationService.sendMatchNotification(fromUserId, toUserId, matchId);
            telegramNotificationService.sendMatchNotification(toUserId, fromUserId, matchId);
        } catch (Exception e) {
            log.error("Error sending match notifications", e);
        }
    }

    /**
     * Отправка уведомлений о лайке асинхронно
     */
    private void sendLikeNotificationsAsync(Long fromUserId, Long toUserId, boolean isSuperLike) {
        try {
            if (isSuperLike) {
                notificationService.sendSuperLikeNotification(toUserId, fromUserId);
                telegramNotificationService.sendSuperLikeNotification(toUserId, fromUserId);
            } else {
                notificationService.sendLikeNotification(toUserId, fromUserId);
                telegramNotificationService.sendLikeNotification(toUserId, fromUserId);
            }
        } catch (Exception e) {
            log.error("Error sending like notifications", e);
        }
    }

    /**
     * Создать мэтч между двумя пользователями (только если не существует)
     */
    @Transactional
    protected Match createMatchIfNotExists(User user1, User user2) {
        // Проверка существования мэтча (под блокировкой, поэтому консистентно)
        Optional<Match> existingMatch = matchRepository.findByUsers(user1.getId(), user2.getId());
        if (existingMatch.isPresent()) {
            log.debug("Match already exists: {}", existingMatch.get().getId());
            return existingMatch.get();
        }

        Match match = Match.builder()
            .user1(user1)
            .user2(user2)
            .status(Match.MatchStatus.ACTIVE)
            .build();

        match = matchRepository.save(match);
        log.info("New match created: id={}, user1={}, user2={}", 
            match.getId(), user1.getId(), user2.getId());
        return match;
    }

    /**
     * Создать мэтч между двумя пользователями (legacy метод)
     * @deprecated Используйте createMatchIfNotExists для безопасности
     */
    @Deprecated
    @Transactional
    protected Match createMatch(User user1, User user2) {
        return createMatchIfNotExists(user1, user2);
    }

    /**
     * Получить все активные мэтчи пользователя
     */
    public List<Match> getActiveMatches(Long userId) {
        return matchRepository.findActiveMatchesByUserId(userId);
    }

    /**
     * Получить активные мэтчи пользователя с пагинацией
     */
    public List<Match> getActiveMatches(Long userId, int offset, int limit) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(offset / limit, limit);
        return matchRepository.findActiveMatchesByUserIdWithPagination(userId, pageable).getContent();
    }

    /**
     * Размэтчить пользователей
     */
    @Transactional
    public void unmatch(Long userId, Long matchId) {
        Match match = matchRepository.findById(matchId)
            .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        if (!match.isParticipant(userId)) {
            throw new IllegalArgumentException("User is not part of this match");
        }

        match.setStatus(Match.MatchStatus.UNMATCHED);
        match.setUnmatchedByUserId(userId);
        match.setUnmatchedAt(LocalDateTime.now());
        
        matchRepository.save(match);
        log.info("Users unmatched: matchId={}, unmatchedBy={}", matchId, userId);
    }

    /**
     * Получить полученные лайки
     */
    public List<Like> getReceivedLikes(Long userId) {
        return likeRepository.findByToUserId(userId);
    }

    /**
     * Получить отправленные лайки
     */
    public List<Like> getSentLikes(Long userId) {
        return likeRepository.findByFromUserId(userId);
    }

    /**
     * Проверить наличие мэтча между пользователями
     */
    public boolean hasMatch(Long user1Id, Long user2Id) {
        Optional<Match> match = matchRepository.findByUsers(user1Id, user2Id);
        return match.isPresent() && match.get().getStatus() == Match.MatchStatus.ACTIVE;
    }

   
    // Получить статистику по мэтчингу
     
    public MatchingStatistics getStatistics(Long userId) {
        String cacheKey = statisticsCacheKey(userId);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        
        if (cached instanceof MatchingStatistics stats) {
            return stats;
        }
        
        long sentLikes = likeRepository.findByFromUserId(userId).size();
        long receivedLikes = likeRepository.findByToUserId(userId).size();
        long activeMatches = matchRepository.countActiveMatchesByUserId(userId);
        
        MatchingStatistics statistics = new MatchingStatistics(sentLikes, receivedLikes, activeMatches);
        redisTemplate.opsForValue().set(cacheKey, statistics, STATS_CACHE_TTL);
        return statistics;
    }

    
     // Сохранить историю свайпа
  
     
     private void saveSwipeHistory(Long fromUserId, Long toUserId, com.bestproduct.dating.domain.entity.SwipeHistory.SwipeType swipeType) {
        try {
            User fromUser = userRepository.findById(fromUserId)
                .orElseThrow(() -> new IllegalArgumentException("From user not found"));
            User toUser = userRepository.findById(toUserId)
                .orElseThrow(() -> new IllegalArgumentException("To user not found"));
            
            // Проверить, есть ли уже запись (не должно быть из-за unique constraint)
            Optional<com.bestproduct.dating.domain.entity.SwipeHistory> existing = swipeHistoryRepository.findByFromUserIdAndToUserId(fromUserId, toUserId);
            
            if (existing.isEmpty()) {
                com.bestproduct.dating.domain.entity.SwipeHistory swipeHistory = com.bestproduct.dating.domain.entity.SwipeHistory.builder()
                    .fromUser(fromUser)
                    .toUser(toUser)
                    .swipeType(swipeType)
                    .build();
                    
                swipeHistoryRepository.save(swipeHistory);
                log.debug("Saved swipe history: {} -> {} ({})", fromUserId, toUserId, swipeType);
                cacheSwipeRecord(fromUserId, toUserId);
            }
        } catch (Exception e) {
            log.error("Error saving swipe history", e);
        }
    }

    private long getDailyLikeCount(Long userId) {
        String key = dailyLikesCacheKey(userId);
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return Long.parseLong(cached.toString());
        }
        long count = likeRepository.countLikesByUserSince(userId, LocalDate.now().atStartOfDay());
        redisTemplate.opsForValue().set(key, count, durationUntilEndOfDay());
        return count;
    }

    private void incrementDailyLikeCount(Long userId) {
        String key = dailyLikesCacheKey(userId);
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redisTemplate.expire(key, durationUntilEndOfDay());
        }
    }

    private Duration durationUntilEndOfDay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfDay = LocalDate.now().plusDays(1).atStartOfDay();
        return Duration.between(now, endOfDay);
    }

    private boolean hasSwipeRecord(Long fromUserId, Long toUserId) {
        String key = swipeCacheKey(fromUserId, toUserId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return true;
        }
        boolean exists = swipeHistoryRepository.findByFromUserIdAndToUserId(fromUserId, toUserId).isPresent();
        if (exists) {
            cacheSwipeRecord(fromUserId, toUserId);
        }
        return exists;
    }

    private void cacheSwipeRecord(Long fromUserId, Long toUserId) {
        redisTemplate.opsForValue().set(swipeCacheKey(fromUserId, toUserId), true, SWIPE_CACHE_TTL);
    }

    private void cacheUserLike(Long fromUserId, Long toUserId) {
        redisTemplate.opsForValue().set(likeCacheKey(fromUserId, toUserId), true, LIKE_CACHE_TTL);
    }

    private boolean hasLike(Long fromUserId, Long toUserId) {
        String key = likeCacheKey(fromUserId, toUserId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return true;
        }
        boolean exists = likeRepository.findByFromUserIdAndToUserId(fromUserId, toUserId).isPresent();
        if (exists) {
            cacheUserLike(fromUserId, toUserId);
        }
        return exists;
    }

    private void invalidateStatisticsCache(Long userId) {
        redisTemplate.delete(statisticsCacheKey(userId));
    }

    private String dailyLikesCacheKey(Long userId) {
        return "matching:likes:daily:" + userId + ":" + LocalDate.now();
    }

    private String swipeCacheKey(Long fromUserId, Long toUserId) {
        return "matching:swipe:" + fromUserId + ":" + toUserId;
    }

    private String likeCacheKey(Long fromUserId, Long toUserId) {
        return "matching:like:" + fromUserId + ":" + toUserId;
    }

    private String statisticsCacheKey(Long userId) {
        return "matching:stats:" + userId;
    }

    public record LikeResult(boolean success, boolean isMatch, Long matchId) {}
    public record MatchingStatistics(long sentLikes, long receivedLikes, long activeMatches) {}
}



