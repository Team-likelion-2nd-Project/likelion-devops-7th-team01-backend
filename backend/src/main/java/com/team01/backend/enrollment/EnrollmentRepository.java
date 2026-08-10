// EnrollmentRepository.java
//
// 이 파일의 역할: Enrollment 엔티티에 대한 DB 접근 기능 자동 생성.
// findByStudentId — "이 학생이 신청한 강의 목록"을 조회할 때 사용
// (시간 중복 체크 로직에서 기존 신청 내역을 가져올 때 씀).

package com.team01.backend.enrollment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    // 메서드 이름만으로 쿼리가 자동 생성됨.
    // findBy + StudentId → "WHERE student_id = ?" 조건으로 자동 변환됨
    // (Spring Data JPA의 쿼리 메서드 기능 — SQL을 직접 안 써도 됨)
    List<Enrollment> findByStudentId(String studentId);
}