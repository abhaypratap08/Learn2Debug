package com.learn2debug.model;

public record AiInsight(
        String summary,
        String likelyCause,
        String confidence
) {
}
