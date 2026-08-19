# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app

# Resolve dependencies in their own layer so source edits do not re-download them.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src

# Stamp the released version into the artifact. MetaMapping writes it into the meta.tag of every
# exported FHIR resource, so a SNAPSHOT version here would end up in biobank data.
ARG APP_VERSION=""
RUN if [ -n "$APP_VERSION" ]; then \
      mvn -B -q versions:set -DnewVersion="$APP_VERSION" -DgenerateBackupPoms=false; \
    fi && \
    mvn -B -DskipTests package && \
    mv target/SampleXChange-*.jar target/SampleXChange.jar
# Tests are skipped deliberately: the release workflow runs the full suite (including the
# Testcontainers system test) in a separate job. There is no Docker socket inside this build.


FROM eclipse-temurin:25-jre-noble

ENV TZ=Europe/Berlin
WORKDIR /app

COPY --from=build /app/target/SampleXChange.jar ./SampleXChange.jar

USER 1001

ENTRYPOINT ["java", "-jar", "/app/SampleXChange.jar"]
