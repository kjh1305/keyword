package com.example.demo.advice;

import lombok.*;

@Getter
@Builder
@ToString
public class ErrorResponse {
    private String message;
    private int status;
}
