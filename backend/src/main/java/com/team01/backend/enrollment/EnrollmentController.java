package com.team01.backend.enrollment;

import com.team01.backend.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private final EnrollmentService service;

    public EnrollmentController(EnrollmentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> enroll(@RequestBody Map<String, Object> request) {
        // studentId는 이제 요청 body가 아니라 JWT에서만 꺼냄 —
        // 클라이언트가 임의의 studentId를 보내 "남의 학생인 척" 신청하는 걸 막기 위함.
        String studentId = JwtUtil.getUserId();
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
        return ResponseEntity.ok(Map.of("courseId", courseId, "status", "CANCELLED"));
    }
}
