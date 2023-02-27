FROM gradle:7.5.1-jdk17-alpine AS build
WORKDIR /home/gradle/src
ENV GRADLE_USER_HOME /gradle

COPY build.gradle settings.gradle gradle.properties ./
RUN gradle clean build --no-daemon || true

COPY --chown=gradle:gradle . .
RUN gradle clean build --info && \
    gradle jacocoTestReport &&  \
    awk -F"," '{ instructions += $4 + $5; covered += $5 } END { print covered, "/", instructions, " instructions covered"; print 100*covered/instructions, "% covered" }' build/reports/jacoco/test/jacocoTestReport.csv && \
    java -Djarmode=layertools -jar build/libs/onkoadt-to-fhir-0.0.1-SNAPSHOT.jar extract

FROM gcr.io/distroless/java17-debian11@sha256:135cf054951c23d8d78b3a7d8c8438f22ca1c15830901d976cb9bf728717334c
WORKDIR /opt/onkoadt-to-fhir

COPY --from=build /home/gradle/src/dependencies/ ./
COPY --from=build /home/gradle/src/spring-boot-loader/ ./
COPY --from=build /home/gradle/src/snapshot-dependencies/ ./
COPY --from=build /home/gradle/src/application/ ./

USER 65532
ARG VERSION=0.0.0
ENV APP_VERSION=${VERSION}
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.JarLauncher"]

ARG GIT_REF=""
ARG BUILD_TIME=""
LABEL maintainer="miracum.org" \
    org.opencontainers.image.created=${BUILD_TIME} \
    org.opencontainers.image.authors="miracum.org" \
    org.opencontainers.image.source="https://gitlab.miracum.org/miracum/etl/streams/ume/onkoadt-to-fhir" \
    org.opencontainers.image.version=${VERSION} \
    org.opencontainers.image.revision=${GIT_REF} \
    org.opencontainers.image.vendor="miracum.org" \
    org.opencontainers.image.vendor="miracum.org" \
    org.opencontainers.image.title="onkoadt-to-fhir" \
    org.opencontainers.image.description="This project contains a Kafka Stream processor that creates FHIR resources from Erlangen Onkostar ADT-XML data and writes them to a FHIR Topic."
