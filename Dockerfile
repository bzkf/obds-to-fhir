FROM docker.io/library/gradle:8.8.0-jdk21@sha256:62c8919321bb78566534c4688a3399b86013ef8a4c45147d9099763d93c4376a AS build
WORKDIR /home/gradle/project

COPY --chown=gradle:gradle . .

RUN --mount=type=cache,target=/home/gradle/.gradle/caches <<EOF
gradle clean build --info --no-daemon
gradle jacocoTestReport --no-daemon
java -Djarmode=layertools -jar build/libs/obds-to-fhir-*.jar extract
EOF

FROM scratch AS test
WORKDIR /test
COPY --from=build /home/gradle/project/build/reports/ .
ENTRYPOINT [ "true" ]

FROM docker.io/library/debian:12.5-slim@sha256:804194b909ef23fb995d9412c9378fb3505fe2427b70f3cc425339e48a828fca AS jemalloc
# hadolint ignore=DL3008
RUN <<EOF
apt-get update
apt-get install -y --no-install-recommends libjemalloc-dev
apt-get clean
rm -rf /var/lib/apt/lists/*
EOF

FROM gcr.io/distroless/java21-debian12:nonroot@sha256:0ba7333a0fb7cbcf90b18073132f4f445870608e8cdc179664f10ce4d00964c2
WORKDIR /opt/obds-to-fhir
ENV LD_PRELOAD="/usr/lib/x86_64-linux-gnu/libjemalloc.so"

COPY --from=jemalloc /usr/lib/x86_64-linux-gnu/libjemalloc.so /usr/lib/x86_64-linux-gnu/libjemalloc.so

COPY --from=build /home/gradle/project/dependencies/ ./
COPY --from=build /home/gradle/project/spring-boot-loader/ ./
COPY --from=build /home/gradle/project/snapshot-dependencies/ ./
COPY --from=build /home/gradle/project/application/ ./

USER 65532:65532
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
