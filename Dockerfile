FROM docker.io/library/gradle:8.13.0-jdk21@sha256:67b8c4bfd2b064e58a7307e2da1fc3881bc03ecc7a57cf61d8b570a02ebfaea2 AS build
WORKDIR /home/gradle/project

COPY --chown=gradle:gradle . .

RUN --mount=type=cache,target=/home/gradle/.gradle/caches <<EOF
set -e
gradle clean build --info
gradle jacocoTestReport
java -Djarmode=layertools -jar build/libs/obds-to-fhir-*.jar extract
EOF

FROM scratch AS test
WORKDIR /test
COPY --from=build /home/gradle/project/build/reports/ .
ENTRYPOINT [ "true" ]

FROM docker.io/library/debian:12.10-slim@sha256:4b50eb66f977b4062683ff434ef18ac191da862dbe966961bc11990cf5791a8d AS jemalloc
# hadolint ignore=DL3008
RUN <<EOF
set -e
apt-get update
apt-get install -y --no-install-recommends libjemalloc-dev
apt-get clean
rm -rf /var/lib/apt/lists/*
EOF

FROM gcr.io/distroless/java21-debian12:nonroot@sha256:a4087689e6bda03258bf1ee4dffe2a86383bc8051eaf2fcf72bdeae5815a6e2d
WORKDIR /opt/obds-to-fhir
ENV LD_PRELOAD="/usr/lib/x86_64-linux-gnu/libjemalloc.so"

COPY --from=jemalloc /usr/lib/x86_64-linux-gnu/libjemalloc.so /usr/lib/x86_64-linux-gnu/libjemalloc.so

COPY --from=build /home/gradle/project/dependencies/ ./
COPY --from=build /home/gradle/project/spring-boot-loader/ ./
COPY --from=build /home/gradle/project/snapshot-dependencies/ ./
COPY --from=build /home/gradle/project/application/ ./

USER 65532:65532
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
