####
# Builds auth-service from source, so `docker compose up -d --build auth-service`
# works on a fresh checkout without running Maven on the host first.
#
# src/main/docker/Dockerfile.jvm is the Quarkus default and is the faster option
# during development: it only copies target/quarkus-app/, but that means
# `./mvnw package` has to have run before the image is built. This file trades
# that for self-containment — point compose at Dockerfile.jvm instead if you
# are already packaging on the host.
#
# Build:
#   docker build -t polygot/auth-service .
#
# Run:
#   docker run -i --rm -p 7110:7110 -e QUARKUS_HTTP_PORT=7110 polygot/auth-service
#
####

# --- build stage --------------------------------------------------------------

# mvnw and .mvn/ are gitignored in this repository, so the wrapper cannot be
# relied on inside the build context — this stage brings its own Maven.
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

COPY pom.xml ./
COPY src ./src

# Tests are skipped here on purpose: they run in CI and on the developer
# machine, where a failure is visible. The cache mount keeps ~/.m2 between
# builds so a rebuild after a source change does not re-download the world.
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

# --- runtime stage ------------------------------------------------------------

# Same base image and layout as Dockerfile.jvm: the four COPY lines are the
# Quarkus fast-jar layout, ordered least- to most-frequently changed.
FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:1.24

ENV LANGUAGE='en_US:en'

COPY --from=build --chown=185 /build/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build --chown=185 /build/target/quarkus-app/*.jar /deployments/
COPY --from=build --chown=185 /build/target/quarkus-app/app/ /deployments/app/
COPY --from=build --chown=185 /build/target/quarkus-app/quarkus/ /deployments/quarkus/

# Same port as the rest of the stack (71x0) without having to pass it in;
# QUARKUS_HTTP_PORT from compose or the Kubernetes manifest still wins.
ENV QUARKUS_HTTP_PORT=7110
EXPOSE 7110
USER 185

ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]