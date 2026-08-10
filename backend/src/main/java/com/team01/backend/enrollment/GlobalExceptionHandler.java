// GlobalExceptionHandler.java
//
// 이 파일의 역할: ApiException이 어디서 던져지든 한 곳에서 잡아서,
// API 명세서에 정의한 { error, code } 형식의 응답으로 통일해서 내려줌.

package com.team01.backend.enrollment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 이제 예외 종류별로 메서드를 나눌 필요 없이, ApiException 하나만 처리하면 됨.
    // 실제 상태코드/코드/메시지는 예외 객체 안에 이미 다 담겨 있음.
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> handleApiException(ApiException e) {
        return ResponseEntity.status(e.getStatus())
            .body(Map.of("error", e.getMessage(), "code", e.getCode()));
    }
}