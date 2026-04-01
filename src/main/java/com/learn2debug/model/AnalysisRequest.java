package com.learn2debug.model;

import jakarta.validation.constraints.NotBlank;

public record AnalysisRequest(
        @NotBlank(message = "code is required")
        String code,
        String level
) {
}
