# EDU 360 API

Spring Boot REST API for the EDU 360 application.

## Requirements
- Java 21
- Maven (or the included Maven wrapper)

## Run locally

From this directory:

```bash
./mvnw spring-boot:run
```

The default profile is `dev`, which uses an in-memory H2 database. The H2 console is enabled only in this local profile at `/h2-console`.

Login is handled by Keycloak, not this app — a Keycloak instance must be reachable before
`spring-boot:run` will start successfully in `dev` (see `docker/keycloak/` for a local
docker-compose setup, and `docs/KEYCLOAK_MIGRATION_PLAN.md` for the full picture).

For Windows, use `mvnw.cmd spring-boot:run`.

## Configuration profiles

- `application.properties`: shared settings only.
- `application-dev.properties`: local H2 database and local Keycloak connection details.
- `application-test.properties`: used automatically by `mvn test`/`mvn verify` — same H2 setup as
  dev, but never runs `DevAdminSeeder`, so automated tests don't need a live Keycloak.
- `application-prod.properties`: PostgreSQL connection, schema validation, disabled H2 console,
  and externally supplied Keycloak connection details.

To run production configuration, set `SPRING_PROFILES_ACTIVE=prod` and provide:
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_BASE_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_ADMIN_CLIENT_ID`,
  `KEYCLOAK_ADMIN_CLIENT_SECRET`

Never commit real credentials. Generate and rotate production secrets through your deployment secret manager.

## Build and test

```bash
./mvnw clean verify
```

## Current architecture status

This repository currently contains a single Spring Boot application with feature packages for authentication, attendance, courses, performance and scores. It is not yet the multi-service EDU 360 V4.0 target architecture. Extracting independently deployable services, introducing Keycloak, PostgreSQL migrations, tenant isolation and messaging requires a planned migration with contract and data compatibility testing; it must not be treated as completed by package reorganization alone.
