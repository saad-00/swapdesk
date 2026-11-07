# SwapDesk

Shift swap governance system for call centers, built with Spring Boot 4, PostgreSQL and Spring Security.

> Work in progress. See [docs/domain-rules.md](docs/domain-rules.md) for the business rules.

## Tech stack

Java 21 · Spring Boot 4 · Spring Data JPA · PostgreSQL · Flyway · Bean Validation · springdoc-openapi · JUnit 5 · Testcontainers

## Run locally

Requirements: JDK 21 and Docker.

```bash
./mvnw spring-boot:run     # starts PostgreSQL from compose.yaml automatically
./mvnw verify              # runs all tests (Testcontainers starts its own PostgreSQL)
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

## Project structure

The code is organised **by feature**. Each package holds its own controller, service, repository, entities and DTOs:

| Package | Responsibility |
|---|---|
| `employee` | Employees, channels, languages, supervisors |
| `schedule` | Schedule cycles and the Excel import |
| `eligibility` | Swap eligibility rules (pure domain logic) |
| `swap` | Swap requests, offers and their workflow |
| `wfm` | Export to WFM and recording its decision |
| `audit` | Audit log |
| `security` | Users and roles |
| `common` | Shared error handling and utilities |
