FROM docker.io/library/gradle:9.5.1-jdk25@sha256:b1220e480f72dd26482af4606e4bc541dda5e0ef350f90b51cf1c5092639ab61 AS build
SHELL ["/bin/bash", "-eo", "pipefail", "-c"]
WORKDIR /home/gradle/project

COPY . .

RUN --mount=type=cache,target=/home/gradle/.gradle/caches <<EOF
set -e
gradle :rest:bootJar --info
JAR_FILE="$(ls /home/gradle/project/rest/build/libs/*.jar)"
java -Djarmode=tools -jar "${JAR_FILE}" extract \
    --layers --launcher --destination ./layers
EOF

FROM docker.io/library/debian:13.5-slim@sha256:28de0877c2189802884ccd20f15ee41c203573bd87bb6b883f5f46362d24c5c2 AS jemalloc
# hadolint ignore=DL3008
RUN <<EOF
set -e
apt-get update
apt-get install -y --no-install-recommends libjemalloc2
apt-get clean
rm -rf /var/lib/apt/lists/*
EOF

FROM gcr.io/distroless/java25-debian13:nonroot@sha256:dade01b669efd3bea3977f73cc196c56f1ee678a71ec8305f84ec15fd5a23c8d
WORKDIR /opt/obds-to-fhir-rest
ENV LD_PRELOAD="/usr/lib/x86_64-linux-gnu/libjemalloc.so.2"

COPY --from=jemalloc /usr/lib/x86_64-linux-gnu/libjemalloc.so.2 /usr/lib/x86_64-linux-gnu/libjemalloc.so.2

COPY --from=build /home/gradle/project/layers/dependencies/ ./
COPY --from=build /home/gradle/project/layers/spring-boot-loader/ ./
COPY --from=build /home/gradle/project/layers/snapshot-dependencies/ ./
COPY --from=build /home/gradle/project/layers/application/ ./

USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
