package com.team01.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 설정.
 *
 * - /health, GET /api/courses → 인증 없이 접근 가능
 * - POST/DELETE /api/enrollments, GET /api/timetable → JWT 필수
 * - Cognito가 발급한 JWT를 자동으로 검증 (application.yml의 jwk-set-uri 사용)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF: REST API는 토큰 기반이라 끈다
            .csrf(csrf -> csrf.disable())

            // 세션 안 쓴다 (매 요청마다 JWT로 인증)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // URL별 인증 규칙
            .authorizeHttpRequests(auth -> auth
                // 헬스체크: 인프라 readinessProbe용, 인증 불필요
                .requestMatchers("/health").permitAll()

                // 강의 목록 조회: 로그인 안 해도 볼 수 있음
                .requestMatchers(HttpMethod.GET, "/api/courses", "/api/courses/**").permitAll()

                // 나머지 API: JWT 필수
                .anyRequest().authenticated()
            )

            // JWT 검증 활성화 (Spring이 JWKS URL에서 공개키 가져와서 자동 검증)
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    /**
     * Cognito JWT의 'sub' claim을 Principal(사용자 식별자)로 사용한다.
     * 기존 코드에서 studentId 고정값을 쓰던 자리를 이걸로 대체하면 된다.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        // Cognito JWT에는 role claim이 없으므로 authority 매핑은 생략
        return converter;
    }
}
