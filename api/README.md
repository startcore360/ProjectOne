# Edu360 API

Spring Boot backend for student management — attendance, scores, and performance tracking with RBAC.

## Quick Start

```bash
cd api
./mvnw spring-boot:run
```

App runs on `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui/index.html`  
H2 Console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:edu360db`, user: `sa`, no password)

## Auth

All endpoints except `/api/auth/**` require a JWT token in the `Authorization` header:

```
Authorization: Bearer <token>
```

### Register

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Prof Smith","email":"smith@edu.com","password":"pass123","role":"TEACHER"}'
```

### Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"smith@edu.com","password":"pass123"}'
```

Both return a JWT token in the response.

## API Overview

| Endpoint | Method | Role | Description |
|---|---|---|---|
| `/api/auth/register` | POST | Public | Register (TEACHER/STUDENT) |
| `/api/auth/login` | POST | Public | Login |
| `/api/courses` | POST | Teacher | Create course |
| `/api/courses` | GET | Both | List my courses |
| `/api/courses/{id}` | GET | Both | Get course |
| `/api/courses/{id}/enroll` | POST | Teacher | Enroll student |
| `/api/attendance` | POST | Teacher | Mark attendance (bulk) |
| `/api/attendance/course/{id}` | GET | Teacher | View course attendance |
| `/api/attendance/me` | GET | Student | View my attendance |
| `/api/attendance/me/course/{id}` | GET | Student | View my attendance for course |
| `/api/scores` | POST | Teacher | Submit scores (bulk) |
| `/api/scores/course/{id}` | GET | Teacher | View course scores |
| `/api/scores/me` | GET | Student | View my scores |
| `/api/scores/me/course/{id}` | GET | Student | View my scores for course |
| `/api/performance/course/{id}` | GET | Teacher | Course performance stats |
| `/api/performance/me` | GET | Student | My performance stats |

## RBAC

- **Teachers** can only modify data for courses they own.
- **Students** can only read their own records.
- Role is enforced at the API level — wrong role returns `403`.

## Tech

Java 21 · Spring Boot 4 · Spring Security (JWT) · Spring Data JPA · H2 · Lombok · SpringDoc OpenAPI
