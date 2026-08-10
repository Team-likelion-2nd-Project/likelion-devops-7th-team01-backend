// CourseController.java
//
// 이 파일의 역할: "강의 목록/단건 조회" HTTP 요청을 받아 응답하는 컨트롤러.
// 단일 백엔드로 통합되면서 패키지 위치만 바뀌었고, 로직 자체는 그대로임.

package com.team01.backend.course;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseRepository repository;

    public CourseController(CourseRepository repository) {
        this.repository = repository;
    }

    // GET /api/courses — 강의 전체 목록 조회
    @GetMapping
    public List<Map<String, Object>> getCourses() {
        return repository.findAll()   // 락 없는 일반 조회 — 목록 보기는 동시성 이슈가 없으므로
            .stream()
            .map(this::toMap)
            .toList();
    }

    // GET /api/courses/{id} — 강의 단건 조회
    // 참고: 이것도 락 없는 조회. 락은 "수강신청 처리 중"에만 필요하고,
    // 단순 조회(구경하는 것)에는 락을 걸 필요가 없음 — 걸면 오히려
    // 불필요하게 다른 요청들을 기다리게 만들어 성능만 나빠짐.
    @GetMapping("/{id}")
    public ResponseEntity<?> getCourse(@PathVariable Long id) {
        return repository.findById(id)
            .map(c -> ResponseEntity.ok(toMap(c)))
            .orElse(ResponseEntity.notFound().build());
    }

    // Course 엔티티를 JSON 응답용 Map으로 변환.
    // Map.ofEntries()를 쓰는 이유: 필드가 11개라 Map.of()의 최대 10개 제한을
    // 넘어섬. ofEntries()는 개수 제한이 없음.
    private Map<String, Object> toMap(Course c) {
        return Map.ofEntries(
            Map.entry("id", c.getId()),
            Map.entry("courseCode", c.getCourseCode()),
            Map.entry("name", c.getName()),
            Map.entry("professor", c.getProfessor()),
            Map.entry("department", c.getDepartment()),
            Map.entry("capacity", c.getCapacity()),
            Map.entry("remaining", c.getRemaining()),
            // status는 저장된 값이 아니라 remaining을 보고 그 자리에서 계산
            Map.entry("credit", c.getCredit()),
            Map.entry("status", c.getRemaining() > 0 ? "OPEN" : "CLOSED"),
            Map.entry("dayOfWeek", c.getDayOfWeek()),
            Map.entry("startTime", c.getStartTime()),
            Map.entry("endTime", c.getEndTime())
        );
    }
}