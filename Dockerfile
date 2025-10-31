FROM maven:3.9.11-eclipse-temurin-25-alpine AS jar-builder

# Set working directory
WORKDIR /app

# Copy parent pom.xml first for better layer caching
COPY pom.xml .

# Copy all module pom.xml files
COPY commons/pom.xml ./commons/
COPY jtt808-protocol/pom.xml ./jtt808-protocol/
COPY jtt808-server/pom.xml ./jtt808-server/

# Copy source code
COPY commons/src ./commons/src
COPY jtt808-protocol/src ./jtt808-protocol/src
COPY jtt808-server/src ./jtt808-server/src

# Build the application
RUN --mount=type=cache,target=/root/.m2 mvn -B -e clean package -P stage -D maven.test.skip=true

FROM bellsoft/liberica-runtime-container:jdk-all-25-cds-musl AS jre-builder

# Required for jlink --strip-debug to work
RUN apk add binutils

COPY --from=jar-builder /app/jtt808-server/target/*.jar runner.jar

# List jar modules
RUN jar xf runner.jar

RUN jdeps \
    --ignore-missing-deps \
    --print-module-deps \
    --multi-release 25 \
    --recursive \
    --class-path 'BOOT-INF/lib/*' \
    runner.jar > modules.txt

# Create a custom Java runtime
RUN $JAVA_HOME/bin/jlink \
    --add-modules $(cat modules.txt) \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --output /jre

FROM alpine:latest

ENV JAVA_HOME=/jre

ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Create non-root user
RUN addgroup --system spring && adduser --system spring --ingroup spring

# Copy smaller jre
COPY --from=jre-builder /jre $JAVA_HOME

# Copy the built JAR from build stage
COPY --from=jar-builder --chown=spring:spring /app/jtt808-server/target/*.jar /app/runner.jar

# Create logs and data directories and set ownership
RUN mkdir -p /logs /data && chown -R spring:spring /logs /data

# Switch to non-root user
USER spring

ARG SPRING_PROFILES_ACTIVE

ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/runner.jar"]
