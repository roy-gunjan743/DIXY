# ---- Stage 1: Build the app with Gradle ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy gradle wrapper and build files first (better Docker layer caching)
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

# Copy source code and build the jar (skip tests for faster image build)
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ---- Stage 2: Run the app on a lightweight JRE ----
FROM eclipse-temurin:21-jre AS run
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]