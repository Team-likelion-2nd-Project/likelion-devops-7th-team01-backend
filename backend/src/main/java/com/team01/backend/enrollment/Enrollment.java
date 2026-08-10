// Enrollment.java
//
// 이 파일의 역할: "누가 어느 강의를 언제 신청했는지"를 DB에 저장하기 위한 엔티티.
// 단일 백엔드로 통합되면서 패키지 위치만 바뀌었고, 필드/로직은 그대로임.

package com.team01.backend.enrollment;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class Enrollment {

    // 신청 건수가 계속 쌓이는 데이터라 id를 손으로 정할 수 없음.
    // GenerationType.IDENTITY → DB가 새 행마다 자동으로 1씩 증가하는 값을 채워줌.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String studentId;   // 신청한 학생 학번 (Auth 붙기 전까지 임시값)
    private Long courseId;      // 어느 강의를 신청했는지 (Course의 id 참조)
    private LocalDateTime enrolledAt;   // 신청 시각

    public Enrollment() {}   // JPA가 필요로 하는 기본 생성자

    // 실제 신청 데이터를 만들 때 쓰는 생성자. enrolledAt은 생성 시점을 자동 기록.
    public Enrollment(String studentId, Long courseId) {
        this.studentId = studentId;
        this.courseId = courseId;
        this.enrolledAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getStudentId() { return studentId; }
    public Long getCourseId() { return courseId; }
    public LocalDateTime getEnrolledAt() { return enrolledAt; }
}