package com.bestproduct.dating.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Конфигурация приложения из application.yml
 */
@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppConfig {

    private Security security = new Security();
    private Geo geo = new Geo();
    private Matching matching = new Matching();
    private Events events = new Events();
    private Moderation moderation = new Moderation();
    private Images images = new Images();

    @Getter
    @Setter
    public static class Security {
        private int minAge = 18;
        private RateLimit rateLimit = new RateLimit();

        @Getter
        @Setter
        public static class RateLimit {
            private int requestsPerMinute = 30;
            private int requestsPerHour = 300;
            private int banThreshold = 1000;
        }
    }

    @Getter
    @Setter
    public static class Geo {
        private int maxSearchRadiusKm = 50;
        private int defaultSearchRadiusKm = 5;
        private int coordinatePrecision = 4;
    }

    @Getter
    @Setter
    public static class Matching {
        private int maxDailyLikes = 100;
        private int maxVisibleProfiles = 50;
    }

    @Getter
    @Setter
    public static class Events {
        private int maxActiveEventsPerUser = 5;
        private int maxParticipants = 20;
        private int minDescriptionLength = 10;
        private int maxDescriptionLength = 500;
    }

    @Getter
    @Setter
    public static class Moderation {
        private boolean enableImageScan = true;
        private boolean enableTextFilter = true;
        private int autoBanOnReports = 5;
        private List<String> blockedDomains = List.of();
    }

    @Getter
    @Setter
    public static class Images {
        private int maxSizeMb = 10;
        private int maxPerProfile = 6;
        private String allowedFormats = "jpg,jpeg,png,webp";
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}



