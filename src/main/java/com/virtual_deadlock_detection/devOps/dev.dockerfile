# ---- Stage 1: Build the app with Gradle ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy gradle wrapper and build files first (better Docker layer caching)
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

# Copy source code and build the jars (skip tests for faster image build)
COPY src src
RUN ./gradlew bootJar jar --no-daemon -x test

# ---- Stage 2: Build the native JVMTI bridge ----
FROM eclipse-temurin:21-jdk AS native-build
WORKDIR /native

RUN DEBIAN_FRONTEND=noninteractive apt-get update \
    && apt-get install -y --no-install-recommends cmake g++ make \
    && rm -rf /var/lib/apt/lists/*

COPY native .
RUN cmake -S . -B build -DCMAKE_BUILD_TYPE=Release \
    && cmake --build build --config Release

# ---- Stage 3: Run the app on a lightweight JRE ----
FROM eclipse-temurin:21-jre AS run
WORKDIR /app

COPY --from=build /app/build/libs/virtual_deadlock_detection-*-SNAPSHOT.jar app.jar
COPY --from=build /app/build/libs/virtual_deadlock_detection-*-SNAPSHOT-plain.jar agent.jar
COPY --from=native-build /native/build/libvirtual_deadlock_agent.so /app/native/libvirtual_deadlock_agent.so

EXPOSE 8080
ENTRYPOINT ["java", "-Djava.library.path=/app/native", "-jar", "app.jar"]
