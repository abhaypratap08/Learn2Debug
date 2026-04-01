package com.learn2debug.model;

import java.util.List;

public record AnalysisResponse(
        String summary,
        int score,
        List<Finding> findings
) {
}
