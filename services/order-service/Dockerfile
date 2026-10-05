# --- build stage --------------------------------------------------------------

# mvnw and .mvn/ are gitignored in this repository, so the wrapper cannot be
# relied on inside the build context — this stage brings its own Maven.
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

COPY pom.xml lombok.config ./
COPY src ./src

# Tests are skipped here on purpose: they run in CI and on the developer
# machine. The cache mount keeps ~/.m2 between builds.
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package \
    && cp target/*.jar /build/app.jar

# --- runtime stage ------------------------------------------------------------

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /build/app.jar /app/app.jar

# config/application.yml is gitignored and holds per-machine values, so the
# image ships its own: docker/application.yml reads everything from the
# environment. Spring Boot picks up ./config/application.yml on its own.
COPY docker/application.yml /app/config/application.yml

RUN useradd --uid 10001 --no-create-home app
USER app
EXPOSE 7120

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
