package com.team01.backend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * JWT에서 사용자 정보를 꺼내는 유틸리티.
 *
 * 기존 코드에서 studentId를 고정값으로 쓰던 자리에
 * JwtUtil.getUserId() 를 넣으면 된다.
 *
 * 예) 기존: String studentId = "fixed-student-1";
 *     변경: String studentId = JwtUtil.getUserId();
 */
public final class JwtUtil {

    private JwtUtil() {}

    /**
     * 현재 요청의 JWT에서 사용자 ID(sub claim)를 꺼낸다.
     * Cognito의 sub = 사용자 고유 UUID.
     */
    public static String getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = (Jwt) auth.getPrincipal();
        return jwt.getSubject();
    }

    /**
     * JWT에서 이메일을 꺼낸다.
     * Cognito는 email을 username으로 설정했으므로 'email' claim에 들어있다.
     */
    public static String getEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = (Jwt) auth.getPrincipal();
        return jwt.getClaimAsString("email");
    }
}
