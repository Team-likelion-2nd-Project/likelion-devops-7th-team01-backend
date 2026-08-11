// EnrollmentController.java
//
// 이 파일의 역할: 수강신청(POST)/취소(DELETE) HTTP 요청을 받아 처리.
// 실패는 예외를 던지는 방식으로 처리하고, 실제 HTTP 응답 변환은
// GlobalExceptionHandler가 한 곳에서 담당함 (여기서 try-catch 안 함).
//
// GET /me는 제거함 — GET /api/timetable(TimetableController.java)이
// 신청목록+강의상세를 정렬/학점합계까지 포함해서 이미 다 내려주므로,
// "내 신청 목록"과 "시간표"를 별개 API로 나눌 필요가 없어짐.

package com.team01.backend.enrollment;

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
}