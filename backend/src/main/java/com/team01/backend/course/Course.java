// Course.java
//
// 이 파일의 역할: "강의"라는 개념을 자바 객체로 표현하고,
// DB의 course 테이블과 1:1로 매핑되는 엔티티(Entity).
// 단일 백엔드로 통합되면서 courseCode, professor, department 필드가 추가됨.

package com.team01.backend.course;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

// @Entity
//   → "이 클래스는 DB 테이블 하나에 대응한다"는 표시.
//     Flyway가 만든 course 테이블과 이 클래스의 필드들이 매칭됨.
@Entity
public class Course {

    // @Id
    //   → 기본키(primary key) 표시. 각 강의를 구별하는 고유 식별자.
    @Id
    private Long id;

    private String courseCode;   // 과목코드 (예: "CSE201")
    private String name;         // 강의명
    private String professor;    // 담당 교수명
    private String department;   // 개설 학과
    private Integer capacity;    // 정원
    private Integer remaining;   // 잔여석
    private Integer credit;      // 학점
    private String dayOfWeek;    // 요일 (MON, TUE 등)
    private String startTime;    // 시작 시간 ("09:00" 형태)
    private String endTime;      // 종료 시간

    // JPA가 객체를 만들 때 필요로 하는 기본 생성자
    public Course() {}

    // getter들 — JPA와 Jackson(JSON 변환기)이 값을 읽어갈 때 사용
    public Long getId() { return id; }
    public String getCourseCode() { return courseCode; }
    public String getName() { return name; }
    public String getProfessor() { return professor; }
    public String getDepartment() { return department; }
    public Integer getCapacity() { return capacity; }
    public Integer getRemaining() { return remaining; }
    public Integer getCredit() { return credit; } 
    public String getDayOfWeek() { return dayOfWeek; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }

    // setRemaining() — 지금까지는 조회만 했지 정원을 직접 깎는 코드가 없었음.
    // DB 트랜잭션 락 방식으로 동시성을 제어하려면, 락을 잡은 상태에서
    // remaining 값을 직접 수정하고 save()해야 하므로 이 setter가 필요함.
    public void setRemaining(Integer remaining) { this.remaining = remaining; }
}