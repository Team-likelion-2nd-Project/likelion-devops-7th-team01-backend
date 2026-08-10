# 수강신청 시스템 — Backend (Course / Enrollment 통합)

수강신청 프로젝트의 백엔드 서비스입니다. Course(강의 조회)와 Enrollment(수강신청/취소)가 단일 Spring Boot 프로젝트로 통합되어 있으며, Docker Compose로 MySQL, Redis까지 포함해 한 번에 실행할 수 있습니다.

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

- `--build`는 **최초 실행 시 반드시 필요**합니다 (이미지가 아직 없으므로). 이후 코드 변경 없이 재시작만 할 때는 `docker compose up -d`만으로 충분합니다.
- `.env` 파일은 `.env.example`을 복사해서 만듭니다. 기본값 그대로 사용해도 로컬 실행에는 문제없습니다.
- 처음 실행 시 이미지 빌드에 1~3분 정도 걸릴 수 있습니다.

## 환경변수 (.env)

| 변수 | 기본값 | 설명 |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | root | MySQL 관리자 비밀번호 |
| `DB_NAME` | backend | 데이터베이스 이름 |
| `DB_USER` | backend | 애플리케이션 접속 계정 |
| `DB_PASSWORD` | backend1234 | 애플리케이션 접속 비밀번호 |
| `DB_HOST_PORT` | 3309 | 호스트에서 MySQL에 접속할 포트 (로컬에 이미 MySQL이 설치되어 3306이 사용 중인 경우가 많아 기본값을 3309로 설정) |
| `REDIS_PORT` | 6379 | 호스트에서 Redis에 접속할 포트 |

`.env`는 `.gitignore`에 포함되어 있으므로 커밋되지 않습니다. 실제 값이 바뀌면 각자 로컬의 `.env`만 수정하면 됩니다.

## 실행 확인

```bash
docker compose ps
```

다음 3개 컨테이너가 모두 `Up` 상태여야 정상입니다.

| 서비스명 | 역할 |
|---|---|
| `backend` | Course/Enrollment 통합 API 서버 (포트 8080) |
| `backend-mysql` | 데이터베이스 |
| `backend-redis` | 캐시/향후 보조 동시성 제어용 (현재는 연결만 준비된 상태) |

로그 확인:

```bash
docker compose logs -f backend
```

## 스키마 및 초기 데이터 — Flyway로 관리

MySQL 컨테이너에 SQL 파일을 마운트하는 대신, 애플리케이션이 시작될 때 **Flyway**가 스키마 생성과 초기 데이터 삽입을 자동으로 처리합니다.

```
src/main/resources/db/migration/
├── V1__create_course_table.sql       # course 테이블 생성
├── V2__insert_sample_courses.sql     # 샘플 강의 15개 삽입
└── V3__create_enrollment_table.sql   # enrollment 테이블 생성
```

- `docker compose up`으로 컨테이너를 처음 띄우면 이 마이그레이션이 자동으로 적용되어, 별도 조치 없이 스키마와 샘플 데이터가 모두 갖춰진 상태로 시작합니다.
- 이미 적용된 마이그레이션 파일(`V1`, `V2`, `V3`)은 **절대 수정하지 마세요.** 스키마를 변경해야 하면 `V4__...` 형태로 새 파일을 추가합니다.
- 적용 이력은 DB의 `flyway_schema_history` 테이블에서 확인할 수 있습니다.

## API 사용법

### Course — 강의 조회

```bash
# 전체 목록
curl localhost:8080/api/courses

# 단건 조회
curl localhost:8080/api/courses/1
```

응답 예시:

```json
{
  "id": 1,
  "courseCode": "CSE201",
  "name": "자료구조",
  "professor": "김민준",
  "department": "컴퓨터공학과",
  "capacity": 30,
  "remaining": 5,
  "status": "OPEN",
  "dayOfWeek": "MON",
  "startTime": "09:00",
  "endTime": "10:30"
}
```

### Enrollment — 수강신청 / 취소

```bash
# 수강신청
curl -X POST localhost:8080/api/enrollments \
  -H "Content-Type: application/json" \
  -d '{"courseId": 1}'
```

성공 응답:

```json
{ "enrollmentId": 1, "courseId": 1, "status": "SUCCESS" }
```

실패 응답 형식은 아래와 같이 통일되어 있습니다.

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
# 수강신청 취소 (위에서 받은 enrollmentId 사용)
curl -X DELETE localhost:8080/api/enrollments/1
```

취소 성공 응답:

```json
{ "courseId": 1, "status": "CANCELLED" }
```

전체 명세는 Wiki > API Specification을 참고하세요.

### 헬스체크

```bash
curl localhost:8080/health
```

## 동작 원리 — 알아두면 좋은 것

- **정원 초과 방지**: `SELECT ... FOR UPDATE`로 강의 row에 DB 트랜잭션 락을 걸고 정원을 확인합니다. 동시에 여러 요청이 몰려도 정원을 초과해 신청이 성공하지 않습니다. (20명 동시 요청 → 정원 10명 기준 정확히 10명만 성공하는 것을 실측 검증함)
- **같은 시간대 중복신청 방지**: 신청 처리 시 같은 학생의 기존 신청 강의들과 시간이 겹치는지 확인합니다.
- **서비스 간 통신 없음**: Course와 Enrollment가 하나의 프로젝트로 통합되어 있어, 강의 정보 조회는 HTTP 호출이 아닌 `CourseRepository` 직접 조회로 처리됩니다.
-- **Redis (보조 캐시)**: DB 트랜잭션 락으로 정원이 확정된 직후, 그 결과를 Redis에 캐시로 동기화합니다. Redis는 판단에 관여하지 않으며, 실패해도 신청/취소 로직에는 영향을 주지 않도록 예외 처리되어 있습니다(Redis 장애 상태에서도 DB 락만으로 정원 초과 방지가 되는 것을 실측 검증함). 배포 환경(EKS)에서는 로컬 Redis 컨테이너 대신 AWS ElastiCache로 교체될 예정이며, `REDIS_HOST`/`REDIS_PORT` 환경변수만 바뀌고 코드 변경은 없습니다.

## 자주 겪는 문제

**포트 충돌 (`port is already allocated`)**

로컬에 이미 설치된 MySQL(예: Homebrew로 설치)이 기본 포트(3306)를 사용 중인 경우가 흔합니다. 아래로 확인합니다.

```bash
sudo lsof -nP -iTCP:3306 -sTCP:LISTEN
```

`mysqld`가 나오면 로컬 MySQL 서비스가 원인입니다. 끄거나(`brew services stop mysql` 및 필요시 `sudo kill -9 <PID>`), `.env`의 `DB_HOST_PORT` 값을 다른 포트로 바꿔서 우회할 수 있습니다(기본값이 이미 3309로 설정되어 있어 대부분 이 문제를 피할 수 있습니다).

**서비스가 시작 직후 종료됨 (`Exited`)**

```bash
docker compose ps -a
docker compose logs <서비스명>
```

네트워크 상태가 꼬인 경우 완전히 초기화 후 재시도합니다.

```bash
docker compose down
docker compose up --build -d
```

**코드를 수정했는데 반영이 안 됨**

```bash
docker compose up --build -d
```

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
└── backend/
    ├── Dockerfile
    ├── build.gradle
    └── src/main/java/com/team01/backend/
        ├── BackendApplication.java
        ├── HealthController.java
        ├── course/
        │   ├── Course.java
        │   ├── CourseRepository.java     # findByIdForUpdate — 트랜잭션 락 조회
        │   └── CourseController.java     # GET /api/courses, GET /api/courses/{id}
        └── enrollment/
            ├── Enrollment.java
            ├── EnrollmentRepository.java
            ├── EnrollmentService.java    # 신청/취소 핵심 로직 (DB 트랜잭션 락 + 시간 중복 체크)
            ├── EnrollmentController.java # POST /api/enrollments, DELETE /api/enrollments/{id}
            ├── ApiException.java
            └── GlobalExceptionHandler.java
```
