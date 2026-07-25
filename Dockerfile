FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon \
    && set -- build/libs/*.jar \
    && [ "$#" -eq 1 ] \
    && cp "$1" /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --gid 10001 pennywise \
    && useradd --uid 10001 --gid pennywise --no-create-home --home-dir /app --shell /usr/sbin/nologin pennywise

COPY --from=build /workspace/app.jar app.jar

EXPOSE 8080

USER pennywise

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseSerialGC", "-Xss512k", "-XX:TieredStopAtLevel=1", "-jar", "/app/app.jar"]
