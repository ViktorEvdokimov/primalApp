# syntax=docker/dockerfile:1

# Сборка: JDK 25 внутри образа, Gradle берёт его как toolchain и ничего не скачивает
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --quiet dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon --quiet bootJar

# Запуск: только JRE и jar, процесс не от root; curl — для healthcheck в docker-compose.yml
FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && groupadd --system primal && useradd --system --gid primal --no-create-home primal
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER primal
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
