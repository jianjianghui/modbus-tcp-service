# Modbus Service (Spring Boot 2.7 / Java 8)

A minimal Spring Boot 2.7 service scaffolded for Java 8 under the base package `com.example.modbus`.

Includes:
- Packages: `config`, `modbus`, `service`, `api`, `model`
- Dependencies: Spring Web, Actuator, Micrometer Prometheus Registry, Jackson, Validation
- JSON logging via Logback + logstash encoder
- Application configuration (application.yml)
- Hello endpoint and health endpoint, plus Actuator endpoints exposed

## Requirements
- Java 8+
- Bash (to use the Maven Wrapper)

## Getting Started

Build and run using the Maven Wrapper:

```bash
# from repo root
./mvnw spring-boot:run
```

Or build a fat jar and run it:

```bash
./mvnw clean package
java -jar target/modbus-service-0.0.1-SNAPSHOT.jar
```

The service will start on port 8080 by default. Key endpoints:
- `GET /api/hello` — returns a JSON greeting. Optional query param `name`.
- `GET /api/health` — simple application health response.
- `GET /actuator/health` — Actuator health endpoint.
- `GET /actuator/prometheus` — Prometheus scrape endpoint.

## Configuration
All configuration is in `src/main/resources/application.yml`.

## Logging
Logs are JSON-formatted using Logback with the logstash encoder. See `src/main/resources/logback-spring.xml`.

