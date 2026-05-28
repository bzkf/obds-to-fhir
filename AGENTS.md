# oBDS-to-FHIR

See [README.md](README.md) for project overview, quick starts, and community information.
See [DEVELOPMENT.md](DEVELOPMENT.md) for technical information on coding guidelines.

## Architecture

Multi-project Gradle repository. Spring Boot Kafka Streams application.

### Requirements

- **Java**: Java 25
- **Build tools**: Gradle 9+
- **Container runtime**: Docker or Podman (use `DOCKER_CMD=podman` for Podman)

### Key Dependencies

- Kafka and Spring Boot versions: See `build.gradle`

### Modules

| Module      | Purpose                                                            |
| ----------- | ------------------------------------------------------------------ |
| `mappings/` | The main mapping logic, mapping oBDS XML to FHIR resources.        |
| `src/`      | The Spring Boot Kafka Streams processer using the mappings library |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md)

- **Code style**: Enforced by spotless and checkstyle
  - Run locally: `./gradlew :spotlessApply`
  - CI will fail if formatting errors are found

## Developer Notes

### Common Development Tasks

**Building:**

- Build project: `./gradlew build` (compiles Java, runs tests)

**Testing:**

- Unit tests only: `./gradlew test`

### Refactoring Guidelines

- IMPORTANT: Always run relevant tests after any code changes
- Prefer incremental changes over large rewrites
- When extracting methods, preserve original function signatures as wrappers initially
- Document any behavior changes in commit messages

## Documentation

User-facing documentation is in `docs/` folder (Markdown format):
