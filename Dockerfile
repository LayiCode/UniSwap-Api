# ---- Build stage: compile + package the Spring Boot fat jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Resolve dependencies first so they cache across rebuilds. The network here
# is flaky (intermittent TLS "bad_record_mac" errors on Maven Central), so each
# step retries up to 5 times and forces wagon to retry aborted transfers.
ARG MVN_COMMON=-Dmaven.wagon.http.retryHandler.count=5 -Dmaven.wagon.http.retryHandler.requestSentEnabled=true -Dmaven.wagon.http.retryHandler.readTimeout=120000 -Dmaven.wagon.http.pool=false
COPY pom.xml .
RUN set -e; \
    for i in 1 2 3 4 5; do \
      mvn -B -q ${MVN_COMMON} dependency:go-offline && exit 0; \
      echo "go-offline attempt $i failed, retrying..."; sleep 5; \
    done; \
    exit 1

COPY src ./src
RUN set -e; \
    for i in 1 2 3 4 5; do \
      mvn -B -q ${MVN_COMMON} -DskipTests package && exit 0; \
      echo "package attempt $i failed, retrying..."; sleep 5; \
    done; \
    exit 1

# ---- Runtime stage: JRE + non-root user + the jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as an unprivileged user; uploads land in a mounted volume, not in the
# image layer. Pre-creating /uploads and chowning it matters: Docker seeds a
# fresh named volume with this directory's ownership, so appuser keeps write
# access (a brand-new volume would otherwise be root-owned).
RUN useradd --create-home --shell /usr/sbin/nologin appuser \
    && mkdir -p /uploads \
    && chown -R appuser:appuser /uploads
COPY --from=build /app/target/UniSwap-0.0.1-SNAPSHOT.jar /app/app.jar
RUN chown -R appuser:appuser /app

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
