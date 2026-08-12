package com.team01.backend.enrollment;

import com.team01.backend.course.Course;
import com.team01.backend.course.CourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimetableControllerTest {

    @Mock
    private EnrollmentService service;

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private TimetableController controller;

    // ── 헬퍼: 테스트용 Enrollment 생성 ──────────────────────────
    // Enrollment에 setter/id 설정이 없으므로 리플렉션으로 id를 주입
    private Enrollment makeEnrollment(Long id, String studentId, Long courseId) {
        Enrollment e = new Enrollment(studentId, courseId);
        try {
            var field = Enrollment.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(e, id);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return e;
    }

    // ── 헬퍼: 테스트용 Course 생성 ──────────────────────────────
    // Course 엔티티도 setter가 없을 수 있어서 리플렉션 사용
    private Course makeCourse(Long id, String courseCode, String name,
                              String professor, String department,
                              int credit, String dayOfWeek,
                              String startTime, String endTime) {
        Course c = new Course();
        try {
            setField(c, "id", id);
            setField(c, "courseCode", courseCode);
            setField(c, "name", name);
            setField(c, "professor", professor);
            setField(c, "department", department);
            setField(c, "credit", credit);
            setField(c, "dayOfWeek", dayOfWeek);
            setField(c, "startTime", startTime);
            setField(c, "endTime", endTime);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return c;
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        var field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    // ================================================================
    //  테스트 케이스
    // ================================================================

    @Test
    @DisplayName("신청 내역이 없으면 빈 시간표 + 총학점 0 반환")
    void emptyEnrollments_returnsEmptyTimetable() {
        // given
        when(service.getMyEnrollments("temp-student")).thenReturn(List.of());

        // when
        Map<String, Object> result = controller.getTimetable();

        // then
        assertThat(result.get("totalCredit")).isEqualTo(0);
        assertThat((List<?>) result.get("courses")).isEmpty();
    }

    @Test
    @DisplayName("신청 1건 → 강의 정보가 정확히 매핑되어 반환")
    void singleEnrollment_mapsCorrectly() {
        // given
        Enrollment enrollment = makeEnrollment(1L, "temp-student", 10L);
        Course course = makeCourse(10L, "CSE201", "자료구조", "김민준",
                "컴퓨터공학과", 3, "MON", "09:00", "10:30");

        when(service.getMyEnrollments("temp-student")).thenReturn(List.of(enrollment));
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));

        // when
        Map<String, Object> result = controller.getTimetable();

        // then
        assertThat(result.get("totalCredit")).isEqualTo(3);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) result.get("courses");
        assertThat(courses).hasSize(1);

        Map<String, Object> first = courses.get(0);
        assertThat(first.get("enrollmentId")).isEqualTo(1L);
        assertThat(first.get("courseId")).isEqualTo(10L);
        assertThat(first.get("courseCode")).isEqualTo("CSE201");
        assertThat(first.get("name")).isEqualTo("자료구조");
        assertThat(first.get("professor")).isEqualTo("김민준");
        assertThat(first.get("department")).isEqualTo("컴퓨터공학과");
        assertThat(first.get("credit")).isEqualTo(3);
        assertThat(first.get("dayOfWeek")).isEqualTo("MON");
        assertThat(first.get("startTime")).isEqualTo("09:00");
        assertThat(first.get("endTime")).isEqualTo("10:30");
    }

    @Test
    @DisplayName("여러 강의 → 요일 순(MON→FRI), 같은 요일이면 시간 순 정렬")
    void multipleEnrollments_sortedByDayThenTime() {
        // given: 일부러 역순으로 넣음 (FRI → WED → MON)
        Enrollment e1 = makeEnrollment(1L, "temp-student", 101L);
        Enrollment e2 = makeEnrollment(2L, "temp-student", 102L);
        Enrollment e3 = makeEnrollment(3L, "temp-student", 103L);

        Course fri = makeCourse(101L, "ENG101", "영어", "박교수", "영어영문", 2, "FRI", "14:00", "15:30");
        Course wed = makeCourse(102L, "MAT201", "선대", "이교수", "수학과", 3, "WED", "10:00", "11:30");
        Course mon = makeCourse(103L, "CSE301", "OS", "최교수", "컴공", 3, "MON", "09:00", "10:30");

        when(service.getMyEnrollments("temp-student")).thenReturn(List.of(e1, e2, e3));
        when(courseRepository.findById(101L)).thenReturn(Optional.of(fri));
        when(courseRepository.findById(102L)).thenReturn(Optional.of(wed));
        when(courseRepository.findById(103L)).thenReturn(Optional.of(mon));

        // when
        Map<String, Object> result = controller.getTimetable();

        // then: MON → WED → FRI 순
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) result.get("courses");

        assertThat(courses).hasSize(3);
        assertThat(courses.get(0).get("dayOfWeek")).isEqualTo("MON");
        assertThat(courses.get(1).get("dayOfWeek")).isEqualTo("WED");
        assertThat(courses.get(2).get("dayOfWeek")).isEqualTo("FRI");
    }

    @Test
    @DisplayName("같은 요일 내에서 시간 순 정렬")
    void sameDayEnrollments_sortedByStartTime() {
        // given: MON에 2과목, 늦은 시간부터 넣음
        Enrollment e1 = makeEnrollment(1L, "temp-student", 201L);
        Enrollment e2 = makeEnrollment(2L, "temp-student", 202L);

        Course late = makeCourse(201L, "CSE401", "AI", "교수A", "컴공", 3, "MON", "14:00", "15:30");
        Course early = makeCourse(202L, "CSE201", "자구", "교수B", "컴공", 3, "MON", "09:00", "10:30");

        when(service.getMyEnrollments("temp-student")).thenReturn(List.of(e1, e2));
        when(courseRepository.findById(201L)).thenReturn(Optional.of(late));
        when(courseRepository.findById(202L)).thenReturn(Optional.of(early));

        // when
        Map<String, Object> result = controller.getTimetable();

        // then: 09:00 → 14:00
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) result.get("courses");

        assertThat(courses.get(0).get("startTime")).isEqualTo("09:00");
        assertThat(courses.get(1).get("startTime")).isEqualTo("14:00");
    }

    @Test
    @DisplayName("총학점이 모든 강의 credit 합산과 일치")
    void totalCredit_sumsCorrectly() {
        // given: 2학점 + 3학점
        Enrollment e1 = makeEnrollment(1L, "temp-student", 301L);
        Enrollment e2 = makeEnrollment(2L, "temp-student", 302L);

        Course c1 = makeCourse(301L, "GEN101", "교양", "교수", "교양학부", 2, "TUE", "10:00", "11:00");
        Course c2 = makeCourse(302L, "CSE301", "DB", "교수", "컴공", 3, "THU", "13:00", "14:30");

        when(service.getMyEnrollments("temp-student")).thenReturn(List.of(e1, e2));
        when(courseRepository.findById(301L)).thenReturn(Optional.of(c1));
        when(courseRepository.findById(302L)).thenReturn(Optional.of(c2));

        // when
        Map<String, Object> result = controller.getTimetable();

        // then
        assertThat(result.get("totalCredit")).isEqualTo(5);
    }

    @Test
    @DisplayName("강의가 DB에서 삭제된 경우(courseId 매칭 실패) → 해당 건은 제외")
    void missingCourse_filteredOut() {
        // given: enrollment는 있지만 course가 없음
        Enrollment e1 = makeEnrollment(1L, "temp-student", 999L);

        when(service.getMyEnrollments("temp-student")).thenReturn(List.of(e1));
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        // when
        Map<String, Object> result = controller.getTimetable();

        // then: 빈 목록, 총학점 0
        assertThat((List<?>) result.get("courses")).isEmpty();
        assertThat(result.get("totalCredit")).isEqualTo(0);
    }
}
