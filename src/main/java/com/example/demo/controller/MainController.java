package com.example.demo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/")
public class MainController {

    @GetMapping
    @ResponseBody
    public String hello() {
        return "Hello, World!";
    }

    @GetMapping({"/portfolio", "/portfolio/"})
    public String portfolio() {
        // forward라 브라우저 주소는 /portfolio로 유지된다 (redirect 시 프록시 뒤에서 http Location이 노출됐던 문제도 회피)
        return "forward:/portfolio/index.html";
    }

    @GetMapping("/portfolio/portfolio.html")
    public String portfolioLegacy() {
        // 구 URL로 들어온 링크 호환용
        return "redirect:/portfolio";
    }

    @GetMapping("/health")
    @ResponseBody
    public ResponseEntity<String> health() {
        // 여기서 DB, 외부 API 등 서비스 상태를 직접 체크할 수 있음
        return ResponseEntity.ok("OK");
    }
}
