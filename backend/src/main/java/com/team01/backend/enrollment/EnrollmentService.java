// EnrollmentService.java
//
// 이 파일의 역할: 수강신청/취소의 핵심 비즈니스 로직.
// 동시성 제어의 근원은 여전히 DB 트랜잭션 락(SELECT ... FOR UPDATE)임 — 이건 안 바뀜.
// M4-2로 추가된 것: DB 처리가 끝난 뒤 Redis에 잔여석 값을 "보조 캐시"로 반영함.
// 이번에 추가된 것: getMyEnrollments() — 이 학생이 신청한 강의 목록을 반환.
// (GET /api/enrollments/me 에서 사용, 강의 상세정보 합치는 건 Controller가 담당)

package com.team01.backend.enrollment;

import com.team01.backend.course.Course;
import com.team01.backend.course.CourseRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;

    // Redis에 명령을 보내는 도구. RedisConfig.java에서 만든 빈이 여기로 주입됨.
    private final RedisTemplate<String, String> redisTemplate;

    public EnrollmentService(EnrollmentRepository enrollmentRepository,
                              CourseRepository courseRepository,
                              RedisTemplate<String, String> redisTemplate) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.redisTemplate = redisTemplate;
    }

    // @Transactional — 이 메서드 전체가 하나의 트랜잭션으로 묶임.
    //   락은 트랜잭션이 끝날 때(커밋/롤백)까지 유지됨.
    // isolation = READ_COMMITTED — MySQL 기본 격리수준.
    //   FOR UPDATE로 이미 명시적 락을 걸고 있으므로 더 높일 필요 없음.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Enrollment enroll(String studentId, Long courseId) {

        // ① 처음부터 락을 걸고 조회 — 이 하나의 객체로 시간 체크, 정원 체크 다 함
        //    (같은 row를 두 번 조회하면 락이 무시되는 버그가 있었어서, 조회를 한 번으로 통합함)
        Course target = courseRepository.findByIdForUpdate(courseId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "존재하지 않는 강의입니다"));

        // ② 시간 중복 체크 — 기존 신청 강의들과 겹치는지 확인
        List<Enrollment> existing = enrollmentRepository.findByStudentId(studentId);
        for (Enrollment e : existing) {
            Course other = courseRepository.findById(e.getCourseId()).orElse(null);
            if (other != null && isTimeConflict(target, other)) {
                throw new ApiException(
                    HttpStatus.CONFLICT, "TIME_CONFLICT", "같은 시간대에 이미 신청한 강의가 있습니다");
            }
        }

        // ③ 정원 체크 — 락이 걸린 상태라 다른 트랜잭션과 경쟁 없이 정확한 값을 봄
        if (target.getRemaining() <= 0) {
            throw new ApiException(
                HttpStatus.CONFLICT, "COURSE_FULL", "정원이 마감된 강의입니다");
        }

        target.setRemaining(target.getRemaining() - 1);
        courseRepository.save(target);

        // ④ 신청 기록 저장 — 여기까지가 "진짜 정답"을 확정하는 DB 처리의 끝
        Enrollment saved = enrollmentRepository.save(new Enrollment(studentId, courseId));

        // ⑤ M4-2로 새로 추가된 부분 — DB가 이미 확정한 remaining 값을
        //    Redis에도 "그대로 따라가게" 반영. 판단은 전혀 안 하고 값만 복사하는 것.
        syncRemainingToRedis(courseId, target.getRemaining());

        return saved;

        // 메서드 종료 → 트랜잭션 커밋 → 락 해제
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long cancel(Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "NOT_FOUND", "신청 내역을 찾을 수 없습니다"));

        Long courseId = enrollment.getCourseId();

        // 취소도 락을 걸고 처리 — 동시 취소 여러 건이 몰려도 정확하게 복구되게
        Course lockedCourse = courseRepository.findByIdForUpdate(courseId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "존재하지 않는 강의입니다"));

        lockedCourse.setRemaining(lockedCourse.getRemaining() + 1);
        courseRepository.save(lockedCourse);

        enrollmentRepository.deleteById(enrollmentId);

        // M4-2 — 취소로 인해 바뀐 remaining 값도 Redis에 동기화
        syncRemainingToRedis(courseId, lockedCourse.getRemaining());

        return courseId;
    }

    // 새로 추가 — 이 학생이 신청한 강의 목록을 그대로 반환.
    // 락이 필요 없는 단순 조회임 (조회만 하는 거라 다른 트랜잭션과 경쟁할 일이 없음).
    // 강의 상세정보(이름, 시간 등)를 합치는 건 Controller에서 처리 —
    // Service는 "신청 기록이 뭔지"만 알고, "그 강의가 어떻게 생겼는지"는 Controller가 조합하게 역할을 나눔.
    public List<Enrollment> getMyEnrollments(String studentId) {
        return enrollmentRepository.findByStudentId(studentId);
    }

    // M4-2로 새로 추가된 메서드.
    // Redis에 "지금 DB가 확정한 잔여석"을 그대로 SET하는 것뿐, 어떤 판단도 하지 않음.
    //
    // try-catch로 감싼 이유가 이 메서드의 핵심임:
    //   Redis가 죽어있거나 응답이 느려도, 여기서 발생한 예외를 절대 밖으로
    //   던지지 않고 조용히 삼킴. 그래야 enroll()/cancel()의 DB 처리가
    //   Redis 장애 때문에 실패하는 일이 없음 — 이게 "Redis는 보조 수단"이라는
    //   완료 조건을 코드로 보장하는 지점.
    private void syncRemainingToRedis(Long courseId, Integer remaining) {
        try {
            redisTemplate.opsForValue().set(
                "course:" + courseId + ":remaining_cache",
                String.valueOf(remaining)
            );
        } catch (Exception e) {
            // 로그만 남기고 넘어감. DB가 이미 진짜 정답을 갖고 있으므로
            // 이 실패는 사용자 응답에 전혀 영향을 주지 않음.
            System.err.println("[Redis 캐시 동기화 실패 - 무시됨] " + e.getMessage());
        }
    }

    // 두 강의의 시간이 겹치는지 판단.
    // 요일이 다르면 무조건 안 겹침, 같으면 시작/종료 시간을 문자열 비교로 확인.
    private boolean isTimeConflict(Course a, Course b) {
        if (!a.getDayOfWeek().equals(b.getDayOfWeek())) return false;
        return a.getStartTime().compareTo(b.getEndTime()) < 0
            && b.getStartTime().compareTo(a.getEndTime()) < 0;
    }
}