# Multi-stage build для оптимизации размера образа

# Stage 1: Build
FROM maven:3.9.5-eclipse-temurin-21 AS build
WORKDIR /app

# Копируем конфигурацию Maven для retry и таймаутов
COPY .mvn .mvn

# Копируем pom.xml и исходники
COPY pom.xml .
COPY src ./src

# Собираем приложение с несколькими попытками при сетевых ошибках
RUN for i in 1 2 3 4 5; do \
        echo "Build attempt $i..." && \
        mvn clean package -DskipTests -B && break || \
        (echo "Build failed, waiting before retry..." && sleep $((i * 10))); \
    done

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Создаем непривилегированного пользователя
RUN addgroup -S spring && adduser -S spring -G spring

# Создаем папку для логов
RUN mkdir -p /app/logs && chown spring:spring /app/logs

USER spring:spring

# Копируем jar из build stage
COPY --from=build /app/target/*.jar app.jar

# Настройка health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Expose порт
EXPOSE 8080

# JVM оптимизация
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

# Запуск приложения
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

