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
# curl is for the container healthcheck. The upgrade is for the CVE gate in CI: the base image ships
# whatever Alpine packages it was built with, and it is rebuilt on its own schedule, not ours — so
# fixes that already exist in the Alpine repo can sit unapplied here for weeks. Upgrading everything
# on top of the base pulls those in at build time and keeps the image current between base bumps.
# `apk upgrade` (not a list of package names) on purpose: naming today's offenders means coming back
# to edit this line for tomorrow's, which is the maintenance nobody does.
RUN apk upgrade --no-cache && apk add --no-cache curl
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

# The image is safe on its own, not only when compose remembers to say so. This used to read
# "docker" — a profile no application-docker.yml has ever defined — so anything starting the image
# without compose (a debug run on the box, a new environment, somebody new to the project) silently
# got NONE of the production settings: pool of 8 instead of 5, no max-connections guarding memory,
# chatty logging, and CORS left at http://localhost:*. Nothing warned. compose still overrides this
# with ${SPRING_PROFILES_ACTIVE:-prod}; the default is now the same answer rather than a trap.
ENV SPRING_PROFILES_ACTIVE=prod

# Uruchamiaj jako nieuprzywilejowany użytkownik (pinned uid 1001 → deterministyczny
# chown wolumenu). /app/uploads jest pre-tworzony i nadany userowi: ŚWIEŻY named volume
# dziedziczy tę własność, więc nowe wdrożenia działają bez ręcznego chown. ISTNIEJĄCY
# (rootowy) wolumen wymaga jednorazowo, przed startem tego obrazu:
#   docker run --rm -v fa_uploads_data_prod:/u alpine chown -R 1001:1001 /u
RUN addgroup -S -g 1001 app && adduser -S -u 1001 -G app app \
    && mkdir -p /app/uploads && chown -R app:app /app
USER app

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
# user.timezone=Europe/Warsaw: monthly billing, payment windows and session dates are driven by
# LocalDate.now()/YearMonth.now() — on the default UTC clock the backend would disagree with Polish
# clients (and the browser) for up to 2h around midnight and at month boundaries.
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=55.0 -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError -XX:TieredStopAtLevel=1 -Duser.timezone=Europe/Warsaw $JAVA_OPTS -jar app.jar"]
