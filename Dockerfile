FROM gradle:8.9.0-jdk17-alpine@sha256:1b2cc1b2eafa12bd1240b419fa26eb8c87bebc6ca4090d097b98dcb64b8be8ed AS build
WORKDIR /home/gradle/src
ENV GRADLE_USER_HOME /gradle

COPY build.gradle settings.gradle gradle.properties ./
RUN gradle clean build --no-daemon || true

COPY --chown=gradle:gradle . .
RUN gradle clean build --info && \
    gradle jacocoTestReport &&  \
    awk -F"," '{ instructions += $4 + $5; covered += $5 } END { print covered, "/", instructions, " instructions covered"; print 100*covered/instructions, "% covered" }' build/jacoco/coverage.csv && \
    java -Djarmode=layertools -jar build/libs/obds-to-fhir-*.jar extract

FROM gcr.io/distroless/java17-debian11@sha256:66dcffeebf676f18b50e0c3544eb5a2636e3254a36b6f2b1aab961ffeb96e059
WORKDIR /opt/obds-to-fhir

COPY --from=build /home/gradle/src/dependencies/ ./
COPY --from=build /home/gradle/src/spring-boot-loader/ ./
COPY --from=build /home/gradle/src/snapshot-dependencies/ ./
COPY --from=build /home/gradle/src/application/ ./

USER 65532
ARG VERSION=0.0.0
ENV APP_VERSION=${VERSION}
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
