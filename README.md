# DatingBot - Telegram бот для знакомств

Telegram бот для знакомств в стиле Tinder с механикой свайпов и поиском пользователей поблизости.

## Назначение

Бот предоставляет платформу для знакомств с механикой свайпов, поиском пользователей поблизости, созданием событий и чатами между совпавшими пользователями.

## Технологический стек

- **Backend**: Java 21, Spring Boot 3.5.6
- **База данных**: PostgreSQL 15 + PostGIS (геолокация)
- **Кеш**: Redis 7.2
- **Telegram API**: telegrambots-spring-boot-starter 6.9.7.1
- **Миграции БД**: Liquibase
- **Обработка изображений**: Google Cloud Vision API
- **Rate Limiting**: Bucket4j 8.10.1
- **Мониторинг**: Spring Actuator, Prometheus, Grafana

## Структура проекта

```
src/main/java/com/bestproduct/dating/
├── domain/               # Доменные сущности
│   └── entity/
│       ├── User.java
│       ├── Profile.java
│       ├── Match.java
│       ├── Like.java
│       ├── Event.java
│       └── Message.java
├── repository/           # Репозитории JPA
│   ├── UserRepository.java
│   ├── ProfileRepository.java
│   └── ...
├── service/              # Бизнес-логика
│   ├── UserService.java
│   ├── ProfileService.java
│   ├── MatchingService.java
│   ├── EventService.java
│   └── ModerationService.java
├── telegram/            # Telegram бот
│   ├── DatingBot.java
│   └── handler/         # Обработчики команд
└── config/             # Конфигурации
    ├── TelegramConfig.java
    └── RedisConfig.java
```

## Основной функционал

- Регистрация и профили пользователей с фото
- Система свайпов (лайки/дизлайки/суперлайки)
- Мэтчи между пользователями
- Чаты между совпавшими пользователями
- Геолокация и поиск пользователей поблизости
- Создание и участие в событиях
- Модерация контента и система жалоб
- Система рейтингов и отзывов
