// HealthController.java
//
// M8-1 — probe용 헬스체크 엔드포인트를 두 개로 나눔.
//
// /health/live  — 프로세스 자체가 응답하는지만 확인, DB 등 외부 의존성은
//   절대 확인하지 않음. 이게 DB까지 확인하면, DB가 잠깐 끊겼을 때
//   "죽었다"고 오판해서 쿠버네티스가 파드를 계속 재시작하는
//   악순환(crash loop)이 생길 수 있음.
//
// /health/ready — 이 파드가 지금 트래픽을 받을 준비가 됐는지 확인.
//   DB 연결까지 실제로 시도해봐서, 안 되면 503을 반환.
//   503을 받으면 쿠버네티스는 트래픽만 안 보내고(재시작은 안 함),
//   DB가 복구되면 자동으로 다시 받기 시작함.

package com.team01.backend;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;

@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health/live")
    public String live() {
        return "OK";
    }

    @GetMapping("/health/ready")
    public ResponseEntity<String> ready() {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(2)) {   // 2초 타임아웃으로 연결 확인
                return ResponseEntity.ok("OK");
            }
        } catch (Exception e) {
            // DB 연결 실패 — 아래에서 503으로 응답
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("NOT READY");
    }
}
