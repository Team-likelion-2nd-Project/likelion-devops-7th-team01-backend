// HealthController.java
//
// 이 파일의 역할: "이 서비스가 살아있나요?"에 답해주는 헬스체크 API.
// 쿠버네티스가 파드 상태를 확인할 때(readinessProbe/livenessProbe) 이 주소로
// 물어보게 될 예정. course나 enrollment 어느 한쪽에 속한 게 아니라
// 앱 전체의 상태를 나타내는 것이므로, 최상위 패키지에 위치시킴.

package com.team01.backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}