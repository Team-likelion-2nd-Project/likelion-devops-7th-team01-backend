// ApiException.java
//
// 이 파일의 역할: 여러 종류의 API 에러(정원마감, 시간중복, 강의없음 등)를
// 각각 별도 클래스로 만들지 않고, 하나의 예외로 "상태코드+코드+메시지"를
// 담아서 표현하기 위한 클래스.

package com.team01.backend.enrollment;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;   // 응답으로 내려갈 HTTP 상태 코드 (409, 404 등)
    private final String code;         // API 명세서의 에러 코드 (COURSE_FULL 등)

    // message는 부모 클래스(RuntimeException)의 생성자에 넘겨서,
    // getMessage()로 나중에 꺼낼 수 있게 함
    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}