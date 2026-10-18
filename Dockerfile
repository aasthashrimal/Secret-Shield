# syntax=docker/dockerfile:1
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM ghcr.io/gitleaks/gitleaks@sha256:691af3c7c5a48b16f187ce3446d5f194838f91238f27270ed36eef6359a574d9 AS gitleaks

FROM eclipse-temurin:21-jre-noble
RUN apt-get update && apt-get install -y --no-install-recommends git curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 secretshield \
    && useradd --system --uid 10001 --gid 10001 --home-dir /app secretshield \
    && mkdir -p /app /workspace \
    && chown -R 10001:10001 /app /workspace
WORKDIR /app
COPY --from=gitleaks /usr/bin/gitleaks /usr/local/bin/gitleaks
COPY --chown=10001:10001 --from=build /build/target/secret-shield-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --chown=10001:10001 config/gitleaks.toml /app/config/gitleaks.toml
USER 10001:10001
ENV GIT_CONFIG_COUNT=1 \
    GIT_CONFIG_KEY_0=safe.directory \
    GIT_CONFIG_VALUE_0=* \
    SCANNER_WORKSPACE=/workspace \
    GITLEAKS_PATH=/usr/local/bin/gitleaks \
    GITLEAKS_CONFIG_PATH=/app/config/gitleaks.toml
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
