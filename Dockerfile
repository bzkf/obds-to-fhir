FROM gradle:8.5.0-jdk17-alpine@sha256:e4f3af5b65fb20115bb8d2054fea4d37e0389aa003735ca6d7b035c9c0d9fb70 AS build
WORKDIR /home/gradle/src
ENV GRADLE_USER_HOME /gradle

COPY build.gradle settings.gradle gradle.properties ./
RUN gradle clean build --no-daemon || true

COPY --chown=gradle:gradle . .
RUN gradle clean build --info && \
    gradle jacocoTestReport &&  \
    awk -F"," '{ instructions += $4 + $5; covered += $5 } END { print covered, "/", instructions, " instructions covered"; print 100*covered/instructions, "% covered" }' build/jacoco/coverage.csv && \
    java -Djarmode=layertools -jar build/libs/obds-to-fhir-*.jar extract

FROM gcr.io/distroless/java17-debian11@sha256:bc99bb6dfb842c0d07410cb341e1a993b4b75198f6e883315d8dc42588844a16
WORKDIR /opt/obds-to-fhir

COPY --from=build /home/gradle/src/dependencies/ ./
COPY --from=build /home/gradle/src/spring-boot-loader/ ./
COPY --from=build /home/gradle/src/snapshot-dependencies/ ./
COPY --from=build /home/gradle/src/application/ ./

USER 65532
ARG VERSION=0.0.0
ENV APP_VERSION=${VERSION}
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
