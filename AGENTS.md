# Repository Guidelines

## Project Structure & Module Organization

This is a multi-module Maven project for a JT/T 808 gateway.

- `pom.xml` is the parent build and declares `commons`, `jtt808-protocol`, and `jtt808-server`.
- `commons/src/main/java/org/yzh/commons` holds shared utilities and common types.
- `jtt808-protocol/src/main/java/org/yzh/protocol` contains protocol encoding, decoding, and message models.
- `jtt808-server/src/main/java/org/yzh/web` contains the Spring Boot server application, web layer, messaging, and persistence integration.
- `*/src/test/java` contains JUnit tests. Current tests include protocol bean tests and server/client integration-style tests.
- `jtt808-server/src/main/resources` contains application configuration. `协议文档/` stores protocol documents and tools.

## Build, Test, and Development Commands

- `mvn clean package` builds all modules and runs tests.
- `mvn test` runs the full test suite.
- `mvn -pl jtt808-protocol -am test` tests the protocol module and required dependencies.
- `mvn -pl jtt808-server -am spring-boot:run` starts the Spring Boot server locally.
- `docker build -t jt808-server .` builds the container image. The Dockerfile packages with the `stage` profile and runs `/app/runner.jar`.

Runtime defaults are in `jtt808-server/src/main/resources/application.yml`: HTTP server `8100`, JT TCP/UDP `7100`, HTTP JT endpoint `7200`, FTP `21`, and RabbitMQ settings.

## Coding Style & Naming Conventions

Use Java with 4-space indentation. Keep packages under `org.yzh.*`. Name classes in `PascalCase`, methods and fields in `camelCase`, and constants in `UPPER_SNAKE_CASE`. Prefer existing Lombok usage where the module already uses it. Keep protocol classes close to `jtt808-protocol`; avoid leaking server concerns into protocol models.

## Testing Guidelines

Tests use JUnit 5 and Spring Boot test support where needed. Name tests `*Test.java` or `*Tests.java`, matching existing files such as `BeanTest.java`, `JT808BeansTest.java`, and `JT808ServiceTest.java`. Add focused tests for protocol changes, especially encoding/decoding and message compatibility. Run `mvn test` before opening a PR.

## Commit & Pull Request Guidelines

User working on branch `development`, commit should start with prefix like: `fix: xxx`, `feat: xxx`

Pull requests should include a clear description, linked issue when applicable, test commands run, and configuration or migration notes. For API or runtime behavior changes, mention affected ports, profiles, queues, or protocol messages.

## Security & Configuration Tips

Do not commit real credentials. Keep RabbitMQ, datasource, and profile-specific values in local or deployment configuration. Avoid committing generated logs under `logs/` or build output under `target/`.
