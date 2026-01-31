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

    @GetMapping("/portfolio")
    public String portfolio() {
        return "redirect:/portfolio/portfolio.html";
    }

    @GetMapping("/health")
    @ResponseBody
    public ResponseEntity<String> health() {
        // 여기서 DB, 외부 API 등 서비스 상태를 직접 체크할 수 있음
        return ResponseEntity.ok("OK");
    }
}
