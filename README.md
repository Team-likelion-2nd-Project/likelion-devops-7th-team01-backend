# 수강신청 시스템 — Backend (Course / Enrollment / Timetable)

수강신청 프로젝트의 백엔드 서비스입니다. Course(강의 조회), Enrollment(수강신청/취소), Timetable(내 시간표 조회)이 단일 Spring Boot 프로젝트로 통합되어 있으며, Docker Compose로 MySQL, Redis까지 포함해 한 번에 실행할 수 있습니다.

## 사전 준비물

- [Docker Desktop](https://www.docker.com/products/docker-desktop) 설치 및 실행
  - 설치 확인: `docker --version`
  - 데몬이 켜져 있는지 확인: `docker ps` (에러 없이 빈 목록이라도 나오면 정상)

## 빠른 시작

```bash
git clone <저장소 주소>
cd <이 폴더>
cp .env.example .env
docker compose up --build -d
```

- `--build`는 **최초 실행 시 반드시 필요**합니다. 이후 코드 변경 없이 재시작만 할 때는 `docker compose up -d`만으로 충분합니다.
- `.env` 파일은 `.env.example`을 복사해서 만듭니다. 기본값 그대로 사용해도 로컬 실행에는 문제없습니다.
- 처음 실행 시 이미지 빌드에 1~3분 정도 걸릴 수 있습니다.
- MySQL이 `healthy` 상태로 뜬 직후 `backend`가 연결에 실패하며 한 번 종료되는 경우가 있습니다. 이 경우 `docker compose up -d`로 `backend`만 다시 띄우면 정상 기동합니다.

## 환경변수 (.env)

| 변수 | 기본값 | 설명 |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | root | MySQL 관리자 비밀번호 |
| `DB_NAME` | backend | 데이터베이스 이름 |
| `DB_USER` | backend | 애플리케이션 접속 계정 |
| `DB_PASSWORD` | backend1234 | 애플리케이션 접속 비밀번호 |
| `DB_HOST_PORT` | 3309 | 호스트에서 MySQL에 접속할 포트 (로컬에 이미 MySQL이 설치되어 3306이 사용 중인 경우가 많아 기본값을 3309로 설정) |
| `REDIS_PORT` | 6379 | 호스트에서 Redis에 접속할 포트 |

`.env`는 `.gitignore`에 포함되어 있으므로 커밋되지 않습니다.

Cognito 관련 설정(`jwk-set-uri`, `issuer-uri`)은 `application.yml`에 직접 값이 들어 있으며, 아직 환경변수로 분리되어 있지 않습니다.

## 실행 확인

```bash
docker compose ps
```

다음 3개 컨테이너가 모두 `Up` 상태여야 정상입니다.

| 서비스명 | 역할 |
|---|---|
| `backend` | Course/Enrollment/Timetable 통합 API 서버 (포트 8080) |
| `backend-mysql` | 데이터베이스 |
| `backend-redis` | 정원 확정 결과의 보조 캐시 |

로그 확인:

```bash
docker compose logs -f backend
```

## 인증 (Cognito JWT)

이 서비스는 AWS Cognito가 발급한 JWT를 사용합니다. `POST/DELETE /api/enrollments`, `GET /api/timetable` 등 대부분의 API는 요청 헤더에 유효한 토큰이 있어야 합니다.

```
Authorization: Bearer <idToken>
```

토큰이 없거나 유효하지 않으면 `401 Unauthorized`가 반환됩니다. 서버는 토큰 자체를 발급하지 않으며, Cognito가 발급한 서명을 JWKS 엔드포인트로 검증만 합니다.

인증 없이 접근 가능한 엔드포인트:

- `GET /health/live`, `GET /health/ready`
- `GET /api/courses`, `GET /api/courses/{id}`

인증된 요청에서 학생 식별자는 클라이언트가 보내는 값이 아니라, 토큰 안의 `sub` claim(`JwtUtil.getUserId()`)에서 서버가 직접 꺼내 사용합니다. 요청 body로 `studentId`를 받던 방식은 보안상 제거되었습니다.

## 스키마 및 초기 데이터 — Flyway로 관리

```
src/main/resources/db/migration/
├── V1__create_course_table.sql
├── V2__insert_sample_courses.sql
├── V3__create_enrollment_table.sql
├── V4__add_credit_to_course.sql
└── V5__update_course_credits.sql
```

- `docker compose up`으로 컨테이너를 처음 띄우면 이 마이그레이션이 자동으로 적용됩니다.
- 이미 적용된 마이그레이션 파일은 **절대 수정하지 마세요.** 스키마를 변경해야 하면 `V6__...` 형태로 새 파일을 추가합니다.
- 적용 이력은 DB의 `flyway_schema_history` 테이블에서 확인할 수 있습니다.

## 커넥션 풀 (HikariCP)

RDS(`db.t4g.micro`)의 낮은 최대 연결 수를 고려해 파드 하나당 DB 연결을 보수적으로 제한합니다.

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 5
      minimum-idle: 2
      connection-timeout: 3000
      max-lifetime: 600000
      idle-timeout: 300000
```

설정 근거와 동시성 재검증 결과는 `docs/hikaricp-connection-pool.md`를 참고하세요. 연결 수를 5개로 줄인 상태에서도 정원 초과 방지 정합성이 깨지지 않는 것을 실측으로 확인했습니다.

## API 사용법

### Course — 강의 조회 (인증 불필요)

```bash
curl localhost:8080/api/courses
curl localhost:8080/api/courses/1
```

### Enrollment — 수강신청 / 취소 (인증 필요)

```bash
curl -X POST localhost:8080/api/enrollments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <idToken>" \
  -d '{"courseId": 1}'
```

성공 응답:

```json
{ "enrollmentId": 1, "courseId": 1, "status": "SUCCESS" }
```

실패 응답 형식:

```json
{ "error": "사용자에게 보여줄 메시지", "code": "ERROR_CODE" }
```

| code | HTTP 상태 | 설명 |
|---|---|---|
| `COURSE_FULL` | 409 | 정원 마감 |
| `TIME_CONFLICT` | 409 | 같은 시간대 중복신청 |
| `COURSE_NOT_FOUND` | 404 | 존재하지 않는 강의 |
| `NOT_FOUND` | 404 | 존재하지 않는 신청 내역 |

```bash
curl -X DELETE localhost:8080/api/enrollments/1 \
  -H "Authorization: Bearer <idToken>"
```

### Timetable — 내 시간표 조회 (인증 필요)

```bash
curl localhost:8080/api/timetable -H "Authorization: Bearer <idToken>"
```

신청한 강의 목록을 요일·시간순으로 정렬하고, 총 학점까지 계산해서 한 번에 반환합니다.

```json
{
  "totalCredit": 6,
  "courses": [
    { "enrollmentId": 1, "courseId": 1, "courseCode": "CSE201", "name": "자료구조", "professor": "김민준", "department": "컴퓨터공학과", "credit": 3, "dayOfWeek": "MON", "startTime": "09:00", "endTime": "10:30" }
  ]
}
```

전체 명세는 Wiki의 API Specification 문서(Course/Enrollment, Timetable 각각)를 참고하세요.

### 헬스체크

```bash
curl localhost:8080/health/live    # 프로세스 생존 여부만 확인
curl localhost:8080/health/ready   # DB 연결까지 확인, 실패 시 503
```

`/health/live`는 쿠버네티스 liveness probe, `/health/ready`는 readiness probe에 대응합니다.

## 동작 원리 — 알아두면 좋은 것

- **정원 초과 방지**: `SELECT ... FOR UPDATE`로 강의 row에 DB 트랜잭션 락을 걸고 정원을 확인합니다. 정원 10명 기준 20명 동시 요청 → 정확히 10명 성공, 정원 1명 기준 200명 동시 요청 → 정확히 1명 성공하는 것을 실측 검증했습니다.
- **같은 시간대 중복신청 방지**: 신청 처리 시 같은 학생의 기존 신청 강의들과 시간이 겹치는지 확인합니다.
- **Redis (보조 캐시)**: DB 트랜잭션 락으로 정원이 확정된 직후, 그 결과를 Redis에 캐시로 동기화합니다. Redis 장애 시에도 신청/취소 로직에는 영향을 주지 않습니다. 배포 환경(EKS)에서는 로컬 Redis 컨테이너 대신 AWS ElastiCache로 교체되며, 환경변수만 바뀌고 코드 변경은 없습니다.
- **CPU 사용 특성**: 신청 처리는 대부분 DB 응답 대기 시간이라 소규모 동시 요청으로는 CPU 사용률이 잘 오르지 않습니다(20명 기준 0.5% 내외). 200명 수준에서는 뚜렷하게 상승하는 것을 확인했습니다 — HPA를 CPU 기준으로 튜닝할 때 참고가 필요합니다.

## CI/CD

`develop` 브랜치에 push되면 GitHub Actions가 자동으로 이미지를 빌드해 ECR에 push하고, Kustomize(`apply -k`)로 EKS에 배포합니다. 워크플로우 정의는 `.github/workflows/deploy.yml`을 참고하세요.

## 자주 겪는 문제

**포트 충돌 (`port is already allocated`)**

```bash
sudo lsof -nP -iTCP:3306 -sTCP:LISTEN
```

`mysqld`가 나오면 로컬 MySQL 서비스가 원인입니다. 끄거나(`brew services stop mysql`), `.env`의 `DB_HOST_PORT` 값을 다른 포트로 바꿔서 우회할 수 있습니다.

**서비스가 시작 직후 종료됨 (`Exited`)**

```bash
docker compose ps -a
docker compose logs backend
```

MySQL 초기화 타이밍 문제인 경우가 많습니다. `docker compose up -d`로 `backend`만 다시 띄우면 해결되는 경우가 대부분입니다. 계속 반복되면:

```bash
docker compose down
docker compose up --build -d
```

**코드를 수정했는데 반영이 안 됨**

```bash
docker compose up --build -d
```

그래도 반영이 안 되면 캐시 문제일 수 있습니다.

```bash
docker compose build --no-cache backend
docker compose up -d
```

**인증이 걸린 API를 토큰 없이 테스트하고 싶을 때**

`SecurityConfig`의 `requestMatchers`에 해당 경로를 임시로 `permitAll()` 추가하고, 관련 컨트롤러의 `JwtUtil.getUserId()`를 임시 고정값으로 바꿔서 테스트할 수 있습니다. **테스트 후 반드시 `git checkout --`으로 원상복구하고, 원복 여부를 `curl`로 401이 다시 나오는지 확인한 뒤 커밋하세요.**

## 종료 / 초기화

```bash
docker compose down      # 컨테이너 정지 및 삭제 (데이터는 유지)
docker compose down -v   # 컨테이너 + 데이터(볼륨)까지 완전히 삭제, 처음 상태로 리셋
```

## 폴더 구조

```
.
├── .env.example
├── docker-compose.yml
├── docs/
│   └── hikaricp-connection-pool.md   # 커넥션 풀 설정 근거 및 검증 결과
└── backend/
    ├── Dockerfile
    ├── build.gradle
    └── src/main/java/com/team01/backend/
        ├── BackendApplication.java
        ├── HealthController.java          # /health/live, /health/ready
        ├── RedisConfig.java
        ├── course/
        │   ├── Course.java
        │   ├── CourseRepository.java      # findByIdForUpdate — 트랜잭션 락 조회
        │   └── CourseController.java      # GET /api/courses, GET /api/courses/{id}
        ├── enrollment/
        │   ├── Enrollment.java
        │   ├── EnrollmentRepository.java
        │   ├── EnrollmentService.java     # 신청/취소 핵심 로직
        │   ├── EnrollmentController.java  # POST /api/enrollments, DELETE /api/enrollments/{id}
        │   ├── TimetableController.java   # GET /api/timetable
        │   ├── ApiException.java
        │   └── GlobalExceptionHandler.java
        └── security/
            ├── SecurityConfig.java        # 인증 규칙, JWT 검증 활성화
            ├── CorsConfig.java            # CORS 설정 (Security 레벨에서 등록)
            └── JwtUtil.java               # JWT에서 사용자 ID/이메일 추출
```
