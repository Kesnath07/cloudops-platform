# syntax=docker/dockerfile:1.10

# ---- Build stage ---------------------------------------------------------------------------
# Runs on the build host's native architecture: the JAR is platform independent, so only the
# small runtime stage needs emulation when cross-building the arm64 (Graviton) image.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml spotbugs-exclude.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests, coverage and static analysis run in CI before the image is built.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q package -DskipTests -Djacoco.skip=true -Dspotbugs.skip=true \
 && java -Djarmode=tools -jar target/cloudops-api.jar extract --layers --launcher --destination target/extracted

# Amazon RDS certificate bundle, used to verify the database server certificate (sslmode=verify-full).
RUN curl -fsSL --proto '=https' -o /workspace/rds-global-bundle.pem \
      https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem

# ---- Runtime stage -------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runtime

RUN apk upgrade --no-cache \
 && addgroup -S -g 10001 cloudops \
 && adduser -S -D -H -u 10001 -G cloudops -h /opt/cloudops cloudops

WORKDIR /opt/cloudops

COPY --from=build --chmod=0444 /workspace/rds-global-bundle.pem ./rds-global-bundle.pem
# Layers ordered from least to most frequently changed to maximise registry layer reuse.
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

USER 10001:10001
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"

HEALTHCHECK --interval=30s --timeout=3s --start-period=90s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
