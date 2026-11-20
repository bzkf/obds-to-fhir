FROM docker.io/library/gradle:9.8.2-jdk25@sha256:e46e15353757ef9c9be0197a243653e88d25ebcd4c3c841057cf839c11fcf898 AS build
SHELL ["/bin/bash", "-eo", "pipefail", "-c"]
WORKDIR /home/gradle/project

COPY . .

RUN --mount=type=cache,target=/home/gradle/.gradle/caches <<EOF
set -e
gradle clean build --info
gradle jacocoTestReport
PROJECT_VERSION="$(gradle --no-configuration-cache -q printVersion)"
java -Djarmode=tools -jar "build/libs/obds-to-fhir-${PROJECT_VERSION}.jar" extract \
    --layers --launcher --destination ./layers
EOF

FROM scratch AS test
WORKDIR /test
COPY --from=build /home/gradle/project/build/reports/ .
ENTRYPOINT [ "true" ]

FROM docker.io/library/debian:13.7-slim@sha256:a72b4433de1cf7d6bae0c4cee74d0547f58601a643b3375f070c51600d63b6b2 AS jemalloc
# hadolint ignore=DL3008
RUN <<EOF
set -e
apt-get update
apt-get install -y --no-install-recommends libjemalloc2
apt-get clean
rm -rf /var/lib/apt/lists/*
EOF

FROM gcr.io/distroless/java25-debian13:nonroot@sha256:9a868974dd82da359adf8c659d9f9a8d5249b138a733def74adbf00f64975db3
WORKDIR /opt/obds-to-fhir
ENV LD_PRELOAD="/usr/lib/x86_64-linux-gnu/libjemalloc.so.2"

COPY --from=jemalloc /usr/lib/x86_64-linux-gnu/libjemalloc.so.2 /usr/lib/x86_64-linux-gnu/libjemalloc.so.2

COPY --from=build /home/gradle/project/layers/dependencies/ ./
COPY --from=build /home/gradle/project/layers/spring-boot-loader/ ./
COPY --from=build /home/gradle/project/layers/snapshot-dependencies/ ./
COPY --from=build /home/gradle/project/layers/application/ ./

USER 65532:65532
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
