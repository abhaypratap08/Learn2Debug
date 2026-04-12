package com.learn2debug.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "learn2debug.ai")
public record Learn2DebugAiProperties(
        boolean enabled,
        boolean inspectNoIssueCases,
        int maxLogicHints
) {
}
