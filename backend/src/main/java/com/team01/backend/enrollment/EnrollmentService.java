// EnrollmentService.java
//
// 이 파일의 역할: 수강신청/취소의 핵심 비즈니스 로직.
// 오늘 마일스톤(#18)의 핵심 — DB 트랜잭션 락(SELECT ... FOR UPDATE)으로 동시성 제어.
//
// 버그 수정: enroll()에서 courseId를 findById()(락 없음)와
// findByIdForUpdate()(락 있음) 두 번 따로 조회했더니, 같은 트랜잭션 안에서
// 영속성 컨텍스트가 이미 로드된 엔티티를 재사용하면서 실제로는 락이
// 안 걸리는 문제가 있었음. 이제 처음부터 findByIdForUpdate() 한 번만 써서
// 그 객체 하나로 시간 체크, 정원 체크를 다 처리하도록 수정함.

package com.team01.backend.enrollment;

import com.team01.backend.course.Course;
import com.team01.backend.course.CourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;

    public EnrollmentService(EnrollmentRepository enrollmentRepository,
                              CourseRepository courseRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Enrollment enroll(String studentId, Long courseId) {

        // ① 처음부터 락을 걸고 조회 — 이 하나의 객체로 시간 체크, 정원 체크 다 함.
        //    (예전엔 findById()로 한 번, findByIdForUpdate()로 또 한 번 조회해서
        //     실제로는 락이 안 먹히는 버그가 있었음 — 이제 조회를 한 번으로 통합)
        Course target = courseRepository.findByIdForUpdate(courseId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "존재하지 않는 강의입니다"));

        // ② 시간 중복 체크. target은 이미 락 걸린 상태고,
        //    other(기존 신청 강의들)는 락 필요 없음 — 그냥 조회만 하는 거니까.
        List<Enrollment> existing = enrollmentRepository.findByStudentId(studentId);
        for (Enrollment e : existing) {
            Course other = courseRepository.findById(e.getCourseId()).orElse(null);
            if (other != null && isTimeConflict(target, other)) {
                throw new ApiException(
                    HttpStatus.CONFLICT, "TIME_CONFLICT", "같은 시간대에 이미 신청한 강의가 있습니다");
            }
        }

        // ③ 정원 체크 — 이제 진짜로 락이 걸린 상태에서 확인되므로,
        //    동시에 여러 트랜잭션이 몰려도 순서대로 하나씩만 이 값을 보게 됨.
        if (target.getRemaining() <= 0) {
            throw new ApiException(
                HttpStatus.CONFLICT, "COURSE_FULL", "정원이 마감된 강의입니다");
        }

        target.setRemaining(target.getRemaining() - 1);
        courseRepository.save(target);

        // ④ 신청 기록 저장
        Enrollment enrollment = new Enrollment(studentId, courseId);
        return enrollmentRepository.save(enrollment);

        // 메서드 종료 → 트랜잭션 커밋 → 락 해제 → 대기하던 다음 요청 처리
    }

    // 신청 취소 — enroll()의 반대 순서. 이 메서드는 원래부터 findByIdForUpdate()
    // 하나만 썼으니 버그 없음, 수정 불필요.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long cancel(Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "NOT_FOUND", "신청 내역을 찾을 수 없습니다"));

        Long courseId = enrollment.getCourseId();

        Course lockedCourse = courseRepository.findByIdForUpdate(courseId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "존재하지 않는 강의입니다"));

        lockedCourse.setRemaining(lockedCourse.getRemaining() + 1);
        courseRepository.save(lockedCourse);

        enrollmentRepository.deleteById(enrollmentId);

        return courseId;
    }

    private boolean isTimeConflict(Course a, Course b) {
        if (!a.getDayOfWeek().equals(b.getDayOfWeek())) return false;
        return a.getStartTime().compareTo(b.getEndTime()) < 0
            && b.getStartTime().compareTo(a.getEndTime()) < 0;
    }
}