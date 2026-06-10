# Technology Stack

**Analysis Date:** 2026-06-10

## Languages

**Primary:**
- Java 25 - All runtime modules (`pom.xml`, `commons/src/main/java`, `jtt808-protocol/src/main/java`, `jtt808-server/src/main/java`)

**Secondary:**
- YAML - Spring profile/runtime configuration (`jtt808-server/src/main/resources/application.yml`, `application-dev.yml`, `application-stage.yml`, `application-prod.yml`)
- XML - Maven and logging configuration (`pom.xml`, `jtt808-server/pom.xml`, `jtt808-server/src/main/resources/log4j2.xml`)
- HTML/JavaScript - Web monitor page (`jtt808-server/src/main/resources/static/ws.html`)

## Runtime

**Environment:**
- JVM 25 (`pom.xml` → `java.version=25`)
- Container runtime with custom jlink JRE (`Dockerfile`)

**Package Manager:**
- Maven multi-module build (`pom.xml` with modules `commons`, `jtt808-protocol`, `jtt808-server`)
- Lockfile: missing

## Frameworks

**Core:**
- Spring Boot 3.5.14 - application framework (`pom.xml`)
- Spring Web MVC (`spring-boot-starter-web`) - HTTP API layer (`jtt808-server/pom.xml`, `jtt808-server/src/main/java/org/yzh/web/controller/JT808Controller.java`)
- NetMC/Netty - JT/T TCP+UDP servers (`jtt808-server/src/main/java/org/yzh/web/config/JTConfig.java`, `jtt808-protocol/pom.xml`)
- Reactor (`Mono`/`Flux`) - async API and SSE streams (`jtt808-server/src/main/java/org/yzh/web/controller/*.java`, `commons/src/main/java/org/yzh/commons/spring/SSEService.java`)

**Testing:**
- Spring Boot Test starter (`jtt808-server/pom.xml`)
- JUnit Jupiter (`jtt808-protocol/pom.xml`)

**Build/Dev:**
- `maven-compiler-plugin` - Java compile target/source (`pom.xml`, module poms)
- `spring-boot-maven-plugin` - boot jar packaging (`jtt808-server/pom.xml`)
- `jdeps` + `jlink` in Docker build - minimized runtime image (`Dockerfile`)

## Key Dependencies

**Critical:**
- `io.github.yezhihao:netmc:4.0.5` - network/session/handler infrastructure (`jtt808-protocol/pom.xml`)
- `io.github.yezhihao:protostar:4.0.5` - protocol schema encode/decode (`jtt808-protocol/pom.xml`)
- `org.springframework.boot:spring-boot-starter-web` - REST endpoints (`jtt808-server/pom.xml`)
- `org.springframework.boot:spring-boot-starter-amqp` - RabbitMQ publishing (`jtt808-server/pom.xml`, `jtt808-server/src/main/java/org/yzh/web/service/MessageProducer.java`)

**Infrastructure:**
- `com.github.ben-manes.caffeine:caffeine` - local caches (`commons/pom.xml`, `jtt808-server/src/main/java/org/yzh/web/config/BeanConfig.java`)
- `com.baomidou:mybatis-plus-spring-boot3-starter` - ORM stack declared (`jtt808-server/pom.xml`)
- `org.mariadb.jdbc:mariadb-java-client`, `com.h2database:h2`, `com.zaxxer:HikariCP` - DB drivers/pool declared (`jtt808-server/pom.xml`)
- `org.springframework.boot:spring-boot-starter-log4j2` - logging backend (`jtt808-server/pom.xml`, `jtt808-server/src/main/resources/log4j2.xml`)
- `org.springdoc:springdoc-openapi-starter-webmvc-ui` + `com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter` - API docs (`pom.xml`, `jtt808-server/src/main/java/org/yzh/web/config/SwaggerConfig.java`)

## Configuration

**Environment:**
- Runtime config is Spring property-driven (`jtt808-server/src/main/resources/application*.yml`)
- Key config groups:
  - `spring.rabbitmq.*` (`jtt808-server/src/main/resources/application.yml`, `application-prod.yml`)
  - `jt-server.jt808.*` (`jtt808-server/src/main/resources/application.yml`, `jtt808-server/src/main/java/org/yzh/web/config/JTProperties.java`)
  - `jt808-service.base-url` (optional HTTP client autoconfig) (`jtt808-protocol/src/main/java/org/yzh/protocol/service/JT808ServiceConfiguration.java`)

**Build:**
- Parent and module build config: `pom.xml`, `commons/pom.xml`, `jtt808-protocol/pom.xml`, `jtt808-server/pom.xml`
- Container build config: `Dockerfile`

## Platform Requirements

**Development:**
- JDK 25 (`pom.xml`)
- Maven 3.9+ (Docker build uses `maven:3.9.16-eclipse-temurin-25-alpine`) (`Dockerfile`)

**Production:**
- Docker image runtime (`Dockerfile`)
- Reachable RabbitMQ broker via `spring.rabbitmq.*` (`jtt808-server/src/main/resources/application*.yml`)
- Exposed ports for HTTP API and JT/T sockets (`jtt808-server/src/main/resources/application.yml`, `jtt808-server/src/main/java/org/yzh/web/config/JTConfig.java`)

---

*Stack analysis: 2026-06-10*
