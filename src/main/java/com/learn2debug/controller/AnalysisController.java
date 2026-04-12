package com.learn2debug.controller;

import com.learn2debug.model.AnalysisRequest;
import com.learn2debug.model.AnalysisResponse;
import com.learn2debug.service.CodeAnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(
    originPatterns = {
        "http://localhost:3000",
        "http://localhost:5173",
        "http://127.0.0.1:3000",
        "http://127.0.0.1:5173",
        "https://*.vercel.app",
        "https://learn2-debug.vercel.app",
        "https://learn2debug.vercel.app"
    },
    allowedHeaders = "*",
    methods = {RequestMethod.GET, RequestMethod.POST},
    allowCredentials = "true"
)
public class AnalysisController {

    private final CodeAnalysisService codeAnalysisService;

    public AnalysisController(CodeAnalysisService codeAnalysisService) {
        this.codeAnalysisService = codeAnalysisService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> analyze(@Valid @RequestBody AnalysisRequest request) {
        AnalysisResponse response = codeAnalysisService.analyze(request.code(), request.level());
        return ResponseEntity.ok(response);
    }
}
