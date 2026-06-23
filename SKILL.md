---
name: ebee-core-skill
description: Maintain and extend the Ebee Core Spring Boot monolith (POS, commission, inventory, order management) following SOLID principles and project-specific conventions.
metadata:
  recommended_model: gpt-5.5
  tags:
    - java
    - spring-boot
    - pos
    - ecommerce
    - fintech
---

# Ebee Core Skill

## When to Use

Use this skill when working on the Ebee Core Spring Boot backend: adding features, fixing bugs, refactoring, writing tests, or updating configuration.

## Conventions

- Java 21, Spring Boot 3.4.x, Maven
- Package layout: `com.ebee.<domain>.{controller,service,repository,entity,dto,mapper,enums,config}`
- No new Lombok; existing Lombok is allowed only because removing it is a long-term migration
- Controllers depend on services, never repositories
- Services should be small and single-responsibility
- Configuration via `@ConfigurationProperties`, not field `@Value`
- Secrets via `.env` (never commit `.env`)
- Flyway for schema migrations

## Critical Rules

1. Never add new Lombok annotations.
2. Never inject a `*Repository` into a controller.
3. Never put business logic in controllers.
4. Never create bidirectional package cycles.
5. Never expose `.env` contents in output.

## Build Commands

```bash
./mvnw clean compile
./mvnw test
./mvnw verify
./mvnw spring-boot:run
```

## Testing Approach

- Unit: Mockito + JUnit 5
- Repository: `@DataJpaTest`
- Integration: `@SpringBootTest` + Testcontainers PostgreSQL
- Architecture: ArchUnit tests in `src/test/java/com/ebee/architecture/`

## Security

- JWT access/refresh tokens with separate secrets
- Role hierarchy SUPER_ADMIN > ADMIN > AGENT
- CORS via `app.cors.allowed-origins`
- IP whitelist only behind trusted reverse proxy

## Reference Files

- `AGENTS.md` — full agent conventions
- `.env.sample` — required environment variables
- `pom.xml` — dependencies and versions
