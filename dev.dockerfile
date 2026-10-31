# ---- Stage 1: Build the native JVMTI bridge ----
FROM eclipse-temurin:21-jdk AS native-build
WORKDIR /app

RUN DEBIAN_FRONTEND=noninteractive apt-get update \
    && apt-get install -y --no-install-recommends cmake g++ make \
    && rm -rf /var/lib/apt/lists/*

COPY native native
# Output lands in src/main/resources/native/<os>-<arch>/... directly, so it
# gets picked up as a normal resource by the Gradle build in the next stage.
RUN cmake -S native -B native/build -DCMAKE_BUILD_TYPE=Release \
    && cmake --build native/build --config Release

# ---- Stage 2: Build the app with Gradle (native lib already in resources) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy gradle wrapper and build files first (better Docker layer caching)
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

# Copy source code, then overlay the native lib built in Stage 1 so it's
# present in src/main/resources before Gradle packages the jars.
COPY src src
COPY --from=native-build /app/src/main/resources/native src/main/resources/native
RUN ./gradlew bootJar jar --no-daemon -x test

# ---- Stage 3: Run the app on a lightweight JRE ----
FROM eclipse-temurin:21-jre AS run
WORKDIR /app

COPY --from=build /app/build/libs/virtual_deadlock_detection-*-SNAPSHOT.jar app.jar
COPY --from=build /app/build/libs/virtual_deadlock_detection-*-SNAPSHOT-plain.jar agent.jar

# Entrypoint derives DB_HOST/DB_PORT from DATABASE_URL when running on Render.
# It does nothing when DB_HOST is already set (docker-compose, Kubernetes).
COPY devops/render-entrypoint.sh /app/entrypoint.sh
RUN sed -i 's/\r$//' /app/entrypoint.sh && chmod +x /app/entrypoint.sh

EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]