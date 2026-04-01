package com.learn2debug.model;

import java.util.List;

public record Finding(
        Severity severity,
        int line,
        String title,
        String explanation,
        String fixSuggestion,
        List<String> relatedDocumentation
) {
}
