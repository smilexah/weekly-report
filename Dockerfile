# syntax=docker/dockerfile:1

# ---- Стадия сборки ----
FROM gradle:9.6.1-jdk21 AS build
WORKDIR /workspace

# Сначала только файлы, нужные для резолва зависимостей - этот слой кешируется отдельно
# и не пересобирается при изменении одних лишь исходников.
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# ---- Финальный образ ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app

ENTRYPOINT ["java", "-jar", "app.jar"]
