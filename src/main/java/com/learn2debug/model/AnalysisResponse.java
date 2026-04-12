package com.learn2debug.model;

import java.util.List;

public record AnalysisResponse(
        String summary,
        int score,
        String level,
        int checksApplied,
        List<Finding> findings
) {
}
