package com.example.demo.controller;

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
        return "portfolio/portfolio.html";
    }
}
