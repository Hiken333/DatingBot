package com.bestproduct.dating.telegram.util;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Утилита для валидации пользовательского ввода
 */
@Component
public class InputValidator {

    // Паттерны для обнаружения нежелательного контента
    private static final Pattern URL_PATTERN = Pattern.compile(
        "(?i)\\b(?:https?://|www\\.|ftp://|@[a-zA-Z0-9_]+|t\\.me/[a-zA-Z0-9_]+)\\S*",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern USERNAME_PATTERN = Pattern.compile(
        "@[a-zA-Z0-9_]+",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "\\b(?:\\+?7|8)?[\\s\\-]?\\(?[0-9]{3}\\)?[\\s\\-]?[0-9]{3}[\\s\\-]?[0-9]{2}[\\s\\-]?[0-9]{2}\\b"
    );
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
    );

    /**
     * Проверить, содержит ли текст нежелательные элементы
     */
    public boolean containsForbiddenContent(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        
        return URL_PATTERN.matcher(text).find() ||
               USERNAME_PATTERN.matcher(text).find() ||
               PHONE_PATTERN.matcher(text).find() ||
               EMAIL_PATTERN.matcher(text).find();
    }

    /**
     * Получить сообщение об ошибке валидации
     */
    public String getValidationErrorMessage() {
        return "❌ В тексте обнаружены недопустимые элементы:\n\n" +
               "• Ссылки (http://, https://, www.)\n" +
               "• Username (@username)\n" +
               "• Номера телефонов\n" +
               "• Email адреса\n\n" +
               "Пожалуйста, удалите их и попробуйте снова.";
    }

    /**
     * Очистить текст от нежелательных элементов
     */
    public String sanitizeText(String text) {
        if (text == null) {
            return null;
        }
        
        // Удаляем URL
        text = URL_PATTERN.matcher(text).replaceAll("[ссылка удалена]");
        
        // Удаляем username
        text = USERNAME_PATTERN.matcher(text).replaceAll("[username удален]");
        
        // Удаляем телефоны
        text = PHONE_PATTERN.matcher(text).replaceAll("[телефон удален]");
        
        // Удаляем email
        text = EMAIL_PATTERN.matcher(text).replaceAll("[email удален]");
        
        return text.trim();
    }

    /**
     * Проверить длину текста
     */
    public boolean isValidLength(String text, int minLength, int maxLength) {
        if (text == null) {
            return minLength == 0;
        }
        return text.length() >= minLength && text.length() <= maxLength;
    }

    /**
     * Получить сообщение об ошибке длины
     */
    public String getLengthErrorMessage(int minLength, int maxLength) {
        return String.format("❌ Текст должен содержать от %d до %d символов.", minLength, maxLength);
    }
}


