package com.bestproduct.dating.service;

import com.bestproduct.dating.config.AppConfig;
import com.bestproduct.dating.domain.entity.Report;
import com.bestproduct.dating.domain.entity.User;
import com.bestproduct.dating.repository.ReportRepository;
import com.bestproduct.dating.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Сервис для модерации контента и жалоб
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final AppConfig appConfig;

    // Паттерн для обнаружения URL
    private static final Pattern URL_PATTERN = Pattern.compile(
        "(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?]))"
    );

    /**
     * Создать жалобу на пользователя
     */
    @Transactional
    public Report createReport(Long reporterId, Long reportedId, Report.ReportReason reason, String description) {
        User reporter = userRepository.findById(reporterId)
            .orElseThrow(() -> new IllegalArgumentException("Reporter not found"));
        User reported = userRepository.findById(reportedId)
            .orElseThrow(() -> new IllegalArgumentException("Reported user not found"));

        // Нельзя пожаловаться на самого себя
        if (reporterId.equals(reportedId)) {
            throw new IllegalArgumentException("Cannot report yourself");
        }

        Report report = Report.builder()
            .reporter(reporter)
            .reported(reported)
            .reason(reason)
            .description(description)
            .status(Report.ReportStatus.PENDING)
            .build();

        report = reportRepository.save(report);

        // Увеличить счетчик жалоб на пользователя
        userService.incrementReportCount(reportedId);

        log.info("Report created: reporterId={}, reportedId={}, reason={}", reporterId, reportedId, reason);
        return report;
    }

    /**
     * Рассмотреть жалобу
     */
    @Transactional
    public void reviewReport(Long reportId, Long moderatorId, Report.ReportStatus newStatus, String notes) {
        Report report = reportRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found"));

        User moderator = userRepository.findById(moderatorId)
            .orElseThrow(() -> new IllegalArgumentException("Moderator not found"));

        // Проверка прав модератора
        if (moderator.getRole() != User.UserRole.MODERATOR && 
            moderator.getRole() != User.UserRole.ADMIN) {
            throw new IllegalArgumentException("User is not a moderator");
        }

        report.setStatus(newStatus);
        report.setReviewedById(moderatorId);
        report.setReviewedAt(LocalDateTime.now());
        report.setModeratorNotes(notes);

        reportRepository.save(report);
        log.info("Report reviewed: reportId={}, moderatorId={}, status={}", reportId, moderatorId, newStatus);
    }

    /**
     * Получить все ожидающие рассмотрения жалобы
     */
    public List<Report> getPendingReports() {
        return reportRepository.findPendingReportsOrderedByDate();
    }

    /**
     * Получить жалобы на конкретного пользователя
     */
    public List<Report> getReportsForUser(Long userId) {
        return reportRepository.findByReportedId(userId);
    }

    /**
     * Валидация и санитизация текста
     */
    public String sanitizeText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        // Экранирование HTML
        String sanitized = StringEscapeUtils.escapeHtml4(text);

        // Удаление потенциально опасных символов
        sanitized = sanitized.replaceAll("[<>\"']", "");

        return sanitized.trim();
    }

    /**
     * Проверка текста на наличие запрещенных URL
     */
    public boolean containsBlockedUrl(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        if (!appConfig.getModeration().isEnableTextFilter()) {
            return false;
        }

        // Проверка на наличие URL
        if (URL_PATTERN.matcher(text.toLowerCase()).find()) {
            log.warn("Blocked URL detected in text: {}", text);
            
            // Проверка на конкретные заблокированные домены
            for (String domain : appConfig.getModeration().getBlockedDomains()) {
                if (text.toLowerCase().contains(domain.toLowerCase())) {
                    log.warn("Blocked domain found: {}", domain);
                    return true;
                }
            }
            
            // По умолчанию блокируем все URL
            return true;
        }

        return false;
    }

    /**
     * Валидация контента сообщения
     */
    public ValidationResult validateContent(String content) {
        if (content == null || content.isBlank()) {
            return new ValidationResult(false, "Content is empty");
        }

        // Проверка на запрещенные URL
        if (containsBlockedUrl(content)) {
            return new ValidationResult(false, "Content contains blocked URLs");
        }

        // Проверка длины
        if (content.length() > 2000) {
            return new ValidationResult(false, "Content is too long");
        }

        return new ValidationResult(true, "Content is valid");
    }

    /**
     * Проверка изображения (заглушка для интеграции с Google Vision API)
     */
    public boolean isImageSafe(String imageUrl) {
        if (!appConfig.getModeration().isEnableImageScan()) {
            return true;
        }

        // TODO: Интеграция с Google Cloud Vision API для сканирования изображений
        // на неприемлемый контент (adult, violence, racy, etc.)
        
        log.debug("Image scan requested for: {}", imageUrl);
        return true;
    }

    /**
     * Получить статистику по модерации
     */
    public ModerationStatistics getStatistics() {
        long totalReports = reportRepository.count();
        long pendingReports = reportRepository.findByStatus(Report.ReportStatus.PENDING).size();
        long resolvedReports = reportRepository.findByStatus(Report.ReportStatus.RESOLVED).size();

        return new ModerationStatistics(totalReports, pendingReports, resolvedReports);
    }

    public record ValidationResult(boolean isValid, String message) {}
    public record ModerationStatistics(long totalReports, long pendingReports, long resolvedReports) {}
}



