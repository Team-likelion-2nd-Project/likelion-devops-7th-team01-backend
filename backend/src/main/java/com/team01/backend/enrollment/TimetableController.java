package com.team01.backend.enrollment;

import com.team01.backend.course.Course;
import com.team01.backend.course.CourseRepository;
import com.team01.backend.security.JwtUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
public class TimetableController {

    private final EnrollmentService service;
    private final CourseRepository courseRepository;

    public TimetableController(EnrollmentService service, CourseRepository courseRepository) {
        this.service = service;
        this.courseRepository = courseRepository;
    }

    @GetMapping("/api/timetable")
    public Map<String, Object> getTimetable() {
        String studentId = JwtUtil.getUserId();

        List<Enrollment> enrollments = service.getMyEnrollments(studentId);

        List<Map<String, Object>> courses = enrollments.stream()
            .map(e -> {
                Course c = courseRepository.findById(e.getCourseId()).orElse(null);
                if (c == null) return null;
                Map<String, Object> m = new HashMap<>();
                m.put("enrollmentId", e.getId());
                m.put("courseId", c.getId());
                m.put("courseCode", c.getCourseCode());
                m.put("name", c.getName());
                m.put("professor", c.getProfessor());
                m.put("department", c.getDepartment());
                m.put("credit", c.getCredit());
                m.put("dayOfWeek", c.getDayOfWeek());
                m.put("startTime", c.getStartTime());
                m.put("endTime", c.getEndTime());
                return m;
            })
            .filter(Objects::nonNull)
            .sorted(Comparator
                .comparingInt((Map<String, Object> m) -> dayOrder((String) m.get("dayOfWeek")))
                .thenComparing(m -> (String) m.get("startTime")))
            .collect(Collectors.toList());

        int totalCredit = courses.stream().mapToInt(m -> (int) m.get("credit")).sum();

        Map<String, Object> result = new HashMap<>();
        result.put("totalCredit", totalCredit);
        result.put("courses", courses);
        return result;
    }

    private int dayOrder(String day) {
        return switch (day) {
            case "MON" -> 0;
            case "TUE" -> 1;
            case "WED" -> 2;
            case "THU" -> 3;
            case "FRI" -> 4;
            default -> 5;
        };
    }
}