# SmartAttend API

Spring Boot 4.1 / Java 17+ API for verified accounts, roster-assigned courses, attendance sessions and PDF reports. This branch adds the frontend integration contract to the engineer's auth service. The deployed Render service may still run an older revision.

The existing Render origin is `https://real-time-attendance-auth-service.onrender.com`. Its free plan may take 30 to 60 seconds to wake after inactivity; this branch must be deployed there before the public frontend can use the new routes.

## Run locally without PostgreSQL or email credentials

The `local` profile uses an in-memory H2 database and prints six-digit verification codes to the API process log. Data disappears when the process stops.

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

The API listens on `http://localhost:2000`. Run the frontend with `VITE_API_PROXY_TARGET=http://127.0.0.1:2000 npm run dev`. The test roster at `src/test/resources/roster.csv` contains an assigned student and an absent student. The local profile and its verification-code logging must never be enabled on a public deployment.

To test with an isolated local PostgreSQL 17 instance on port 55432 (macOS/Homebrew), run:

```bash
brew install postgresql@17 openjdk@21 maven
bash scripts/start-local-postgres.sh
JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home" \
  SPRING_PROFILES_ACTIVE=local,postgres-local mvn spring-boot:run
```

The second profile overrides H2 with real PostgreSQL and keeps the local email-code logger. It listens on API port 2000 by default. In another terminal, from the frontend repository root, run:

```bash
VITE_API_PROXY_TARGET=http://127.0.0.1:2000 npm run dev
```

Open the URL printed by Vite. To create disposable lecturer and student accounts, a confirmed roster, check-in, history and PDF, run `bash scripts/smoke-local-postgres.sh` from this backend directory. It prints the test account emails and password. The test reads six-digit verification codes from the disposable database; interactive registrations show their codes in the API log. The smoke accounts use a synthetic face descriptor, so use a fresh student registration for a real camera check. Existing test data remains after an API restart. The database binds to localhost with trust authentication and must never be exposed publicly.

For a regular deployment, configure `POSTGRES_URL`, `POSTGRES_USERNAME`, `POSTGRES_PASSWORD`, `JWT_SECRET`, `GMAIL_CLIENT_ID`, `GMAIL_CLIENT_SECRET`, `GMAIL_REFRESH_TOKEN`, and `GMAIL_SENDER` as required by `application.properties` and `EmailService`. Review the existing PostgreSQL schema before allowing Hibernate `ddl-auto=update` to change it. No production database migration was run during this work.

## API contract

Protected routes require `Authorization: Bearer <token>`. The service gets the acting identity from the token subject. A student must verify email and enroll a face descriptor before accessing non-auth API routes; otherwise the API returns `403` with `EMAIL_VERIFICATION_REQUIRED` or `FACE_ENROLLMENT_REQUIRED`. `GET /api/auth/me` and `POST /api/auth/onboard-face` remain available so the student can finish setup. Lecturers do not need face enrollment. Student email and matric number must both match a confirmed roster entry. Session codes, lecturer coordinates, rosters and attendance records are shown only to course managers.

| Purpose | Route |
| --- | --- |
| Registration and email | `POST /api/auth/register`, `POST /api/auth/verify-email`, `POST /api/auth/resend-verification?email=...` |
| Login and profile | `POST /api/auth/login`, `GET /api/auth/me` |
| Student face enrollment | `POST /api/auth/onboard-face` with `{facialEmbedding:"[128 numbers]"}` |
| Course setup | `POST /api/courses`, `POST /api/courses/{courseCode}/roster-upload` (multipart `file`), `POST /api/courses/{courseCode}/confirm-roster` |
| Course lookup | `GET /api/courses/mine`, `GET /api/courses/{courseId}` |
| Sessions | `POST /api/attendance/sessions` with `{courseCode,latitude,longitude}`, `GET /api/attendance/sessions/active`, `GET /api/attendance/sessions/history`, `GET /api/attendance/sessions/{id}`, `POST /api/attendance/sessions/{id}/close` |
| Check-in | `POST /api/attendance/sessions/{id}/check-ins` with `{code,latitude,longitude,facialEmbedding}` |
| Reporting | `GET /api/attendance/sessions/{id}/records`, `GET /api/reports/sessions/{id}/pdf` |

Each session lasts five minutes. The server enforces a 100 m geofence around the lecturer's start location. Completed student history includes `myStatus` (`PRESENT` or `ABSENT`) and `myCheckedInAt`. The PDF uses the session's immutable roster snapshot and includes absent students. Browser-generated face descriptors do not prove liveness and GPS coordinates can be spoofed.

## Verification

`mvn test` runs focused attendance service tests, including the student enrollment gate. The local HTTP smoke test covered registration, verification, login, CSV roster, blocked student access before enrollment, course assignment after synthetic face enrollment, start, geofence failure, successful and duplicate check-in, closure, history and PDF authorization. Real camera capture, deployed email delivery, production PostgreSQL migration and public CORS still require verification after deployment.
