FROM docker.io/library/gradle:9.1.0-jdk25@sha256:d2f954187670397de6dd42c5c3a9d4535409b590059c6d248ff2a59ba67cecc3 AS build
SHELL ["/bin/bash", "-eo", "pipefail", "-c"]
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

FROM docker.io/library/debian:13.1-slim@sha256:66b37a5078a77098bfc80175fb5eb881a3196809242fd295b25502854e12cbec AS jemalloc
# hadolint ignore=DL3008
RUN <<EOF
set -e
apt-get update
apt-get install -y --no-install-recommends libjemalloc2
apt-get clean
rm -rf /var/lib/apt/lists/*
EOF

FROM gcr.io/distroless/java25-debian13:nonroot@sha256:427b96593928d4c904de5dccec7c9b87aeb163c8f9f28f3212ee0b7f5f445746
WORKDIR /opt/obds-to-fhir
ENV LD_PRELOAD="/usr/lib/x86_64-linux-gnu/libjemalloc.so.2"

COPY --from=jemalloc /usr/lib/x86_64-linux-gnu/libjemalloc.so.2 /usr/lib/x86_64-linux-gnu/libjemalloc.so.2

COPY --from=build /home/gradle/project/dependencies/ ./
COPY --from=build /home/gradle/project/spring-boot-loader/ ./
COPY --from=build /home/gradle/project/snapshot-dependencies/ ./
COPY --from=build /home/gradle/project/application/ ./

USER 65532:65532
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
