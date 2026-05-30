FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
ARG APP_VERSION=dev

COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle settings.gradle gradle.properties ./
COPY src src

RUN chmod +x gradlew
RUN APP_VERSION=$APP_VERSION ./gradlew bootJar -x test

FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8081
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+UseStringDeduplication $JAVA_OPTS -jar app.jar"]
