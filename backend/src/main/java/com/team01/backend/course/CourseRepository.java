// CourseRepository.java
//
// 이 파일의 역할: Course 엔티티에 대한 DB 접근 기능을 자동으로 만들어주는 창구.
// 기본적인 findAll(), findById() 외에, 동시성 제어에 필요한
// "락을 걸면서 조회하는" 특수 메서드를 하나 추가로 정의함.

package com.team01.backend.course;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    // @Lock(LockModeType.PESSIMISTIC_WRITE)
    //   → 이 메서드로 조회하면 실제 SQL이 SELECT ... FOR UPDATE로 나감.
    //     "비관적(pessimistic)"이라는 이름의 의미: 다른 트랜잭션이
    //     이 데이터를 동시에 건드릴 거라고 미리 "비관적으로" 가정하고,
    //     아예 처음부터 잠가버리는 방식. (반대는 "낙관적 락"으로,
    //     일단 다 같이 읽게 하고 나중에 충돌 여부만 확인하는 방식)
    //
    // @Query — JPQL(엔티티 기준 쿼리 언어)로 직접 조회 조건을 명시.
    //   findById()라는 기본 제공 메서드도 있지만, 거기엔 락 옵션을
    //   자유롭게 못 붙이므로 이렇게 직접 쿼리를 써서 락을 명시적으로 건 것.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Course c WHERE c.id = :id")
    Optional<Course> findByIdForUpdate(Long id);
}