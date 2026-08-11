// EnrollmentController.java
//
// 이 파일의 역할: 수강신청(POST)/취소(DELETE)/내 신청목록 조회(GET /me) HTTP 요청을
// 받아 처리. 실패는 예외를 던지는 방식으로 처리하고, 실제 HTTP 응답 변환은
// GlobalExceptionHandler가 한 곳에서 담당함 (여기서 try-catch 안 함).
//
// GET /me 추가 — 이 학생이 신청한 강의 목록을, 강의 상세정보(이름/시간 등)까지
// 합쳐서 반환. 시간표 화면(차니 담당)이 이 API 하나로 완성될 수 있게 하는 게 목적이라
// CourseRepository를 새로 주입받음.

package com.team01.backend.enrollment;

import com.team01.backend.course.Course;
import com.team01.backend.course.CourseRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private final EnrollmentService service;
    private final CourseRepository courseRepository;

    public EnrollmentController(EnrollmentService service, CourseRepository courseRepository) {
        this.service = service;
        this.courseRepository = courseRepository;
    }

    @PostMapping
    public ResponseEntity<?> enroll(@RequestBody Map<String, Object> request) {
        // 테스트를 위해 studentId를 요청에서 받을 수 있게 함.
        // 없으면 기존처럼 "temp-student" 기본값 사용.
        // Auth 붙으면 이 줄은 JWT에서 studentId를 꺼내는 코드로 교체될 예정.
        String studentId = request.getOrDefault("studentId", "temp-student").toString();
        Long courseId = Long.valueOf(request.get("courseId").toString());

        Enrollment saved = service.enroll(studentId, courseId);

        return ResponseEntity.ok(Map.of(
            "enrollmentId", saved.getId(),
            "courseId", saved.getCourseId(),
            "status", "SUCCESS"
        ));
    }

    @DeleteMapping("/{enrollmentId}")
    public ResponseEntity<?> cancel(@PathVariable Long enrollmentId) {
        Long courseId = service.cancel(enrollmentId);

        return ResponseEntity.ok(Map.of(
            "courseId", courseId,
            "status", "CANCELLED"
        ));
    }

    // 새로 추가 — GET /api/enrollments/me : 내 신청 목록 (강의 상세정보 포함)
    @GetMapping("/me")
    public List<Map<String, Object>> getMyEnrollments() {
        // TODO: JWT 연동되면 studentId를 토큰에서 추출하도록 교체.
        // (enroll()처럼 요청 파라미터로 받게 하지 않은 이유: 조회 API는
        //  "누가 요청했는지"가 인증에서 나와야 자연스럽고, 남의 신청목록을
        //  파라미터로 아무나 조회할 수 있게 열어두면 안 되기 때문)
        String studentId = "temp-student";

        List<Enrollment> enrollments = service.getMyEnrollments(studentId);

        return enrollments.stream().map(e -> {
            Course course = courseRepository.findById(e.getCourseId()).orElse(null);

            Map<String, Object> map = new HashMap<>();
            map.put("enrollmentId", e.getId());
            map.put("courseId", e.getCourseId());
            if (course != null) {
                map.put("courseCode", course.getCourseCode());
                map.put("name", course.getName());
                map.put("professor", course.getProfessor());
                map.put("department", course.getDepartment());
                map.put("credit", course.getCredit());
                map.put("dayOfWeek", course.getDayOfWeek());
                map.put("startTime", course.getStartTime());
                map.put("endTime", course.getEndTime());
            }
            return map;
        }).toList();
    }
}