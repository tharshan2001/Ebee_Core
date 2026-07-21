package com.ebee.common.controller;

import com.ebee.common.response.ApiResponse;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class ApiFallbackController implements ErrorController {

    @RequestMapping("/api/**")
    public ResponseEntity<ApiResponse<Void>> handleUnmatched() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Not found"));
    }

    @RequestMapping("/error")
    public ResponseEntity<ApiResponse<Void>> handleError(HttpServletRequest request) {
        Integer statusCode = (Integer) request.getAttribute("jakarta.servlet.error.status_code");
        if (statusCode == null) {
            statusCode = 404;
        }
        return ResponseEntity.status(statusCode)
                .body(ApiResponse.error("Not found"));
    }
}
