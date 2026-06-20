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
# JVM tuning for a ~1GB RAM / 2 vCPU box (mem_limit 384m — see CLAUDE.md "Infrastruktura"):
# - MaxRAMPercentage=55 / MaxMetaspaceSize=128m -> heap+non-heap stays under the limit;
#   ExitOnOutOfMemoryError -> clean restart (compose restart: unless-stopped) over a wedged JVM.
# - TieredStopAtLevel=1 -> C1-only JIT: skips the C2 optimizing compiler. On 2 vCPU the C2
#   threads contend with the startup thread for the cores, stretching boot; dropping C2 hands
#   those cores back to startup, ~halving the boot-time 502 window on deploy (CPU is idle here,
#   RAM is the bottleneck). Cost is lower peak throughput of hot paths — irrelevant at this load
#   and a one-flag revert if traffic grows. (CDS evaluated and dropped: ~4% gain wasn't worth
#   the build-time no-DB training run breaking silently on future Spring upgrades.)
# GC left to ergonomics (SerialGC on <2GB). $JAVA_OPTS kept as passthrough.
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=55.0 -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError -XX:TieredStopAtLevel=1 $JAVA_OPTS -jar app.jar"]
