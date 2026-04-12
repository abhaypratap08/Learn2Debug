package com.learn2debug.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn2debug.config.Learn2DebugAiProperties;
import com.learn2debug.model.AiInsight;
import com.learn2debug.model.AnalysisResponse;
import com.learn2debug.model.Finding;
import com.learn2debug.model.Severity;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Primary
@ConditionalOnBean(ChatClient.Builder.class)
@ConditionalOnProperty(prefix = "learn2debug.ai", name = "enabled", havingValue = "true")
public class SpringAiAnalysisEnrichmentService implements AnalysisEnrichmentService {

    private static final String LOGIC_ISSUE_DOC = "https://docs.junit.org/current/user-guide/";

    private final ChatClient chatClient;
    private final Learn2DebugAiProperties properties;
    private final ObjectMapper objectMapper;

    public SpringAiAnalysisEnrichmentService(ChatClient.Builder chatClientBuilder,
                                             Learn2DebugAiProperties properties,
                                             ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AnalysisResponse enrich(String code, AnalysisResponse response) {
        try {
            if (containsOnlyNoIssueTip(response.findings())) {
                List<Finding> logicHints = generateLogicHints(code, response.level());
                if (!logicHints.isEmpty()) {
                    return rebuildResponse(response, logicHints);
                }
                return response;
            }

            List<Finding> enrichedFindings = enrichDeterministicFindings(code, response.findings());
            return rebuildResponse(response, enrichedFindings);
        } catch (RuntimeException ignored) {
            return response;
        }
    }

    private List<Finding> enrichDeterministicFindings(String code, List<Finding> findings) {
        if (findings.isEmpty()) {
            return findings;
        }

        AiFindingBatchResponse aiResponse = chatClient.prompt()
                .system("""
                        You are a senior Java software engineer helping explain analyzer results.
                        Use the provided findings and source code as evidence.
                        Do not invent new compile errors or line numbers.
                        For each finding, keep the same line and title.
                        Explain the likely cause in plain English and keep confidence honest: high, medium, or low.
                        Return only structured data.
                        """)
                .user(user -> user
                        .text("""
                                Source code:
                                ```java
                                {code}
                                ```

                                Existing findings:
                                {findingsJson}

                                Explain each finding.
                                """)
                        .param("code", code)
                        .param("findingsJson", findingsJson(findings)))
                .call()
                .entity(AiFindingBatchResponse.class);

        if (aiResponse == null || aiResponse.insights() == null || aiResponse.insights().isEmpty()) {
            return findings;
        }

        Map<String, AiFindingExplanation> explanationByKey = new HashMap<>();
        for (AiFindingExplanation insight : aiResponse.insights()) {
            explanationByKey.put(findingKey(insight.line(), insight.title()), insight);
        }

        List<Finding> enrichedFindings = new ArrayList<>();
        for (Finding finding : findings) {
            AiFindingExplanation explanation = explanationByKey.get(findingKey(finding.line(), finding.title()));
            if (explanation == null) {
                enrichedFindings.add(finding);
                continue;
            }

            enrichedFindings.add(new Finding(
                    finding.severity(),
                    finding.line(),
                    finding.title(),
                    finding.explanation(),
                    finding.fixSuggestion(),
                    finding.relatedDocumentation(),
                    new AiInsight(
                            clean(explanation.summary()),
                            clean(explanation.likelyCause()),
                            normalizeConfidence(explanation.confidence())
                    )
            ));
        }

        return enrichedFindings;
    }

    private List<Finding> generateLogicHints(String code, String level) {
        if (!properties.inspectNoIssueCases()) {
            return List.of();
        }

        AiLogicIssueResponse aiResponse = chatClient.prompt()
                .system("""
                        You are a senior Java engineer reviewing code that currently has no compiler or rule-based findings.
                        Find only concrete, evidence-based logic issues or suspicious algorithm mistakes.
                        Each issue must point to a specific line from the provided code.
                        If the code may be valid and evidence is weak, return an empty list.
                        Keep confidence honest: high, medium, or low.
                        Return only structured data.
                        """)
                .user(user -> user
                        .text("""
                                Review this Java code for likely logic bugs.
                                The current analyzer level is {level}.
                                Return at most {maxLogicHints} issues.

                                ```java
                                {code}
                                ```
                                """)
                        .param("code", code)
                        .param("level", level)
                        .param("maxLogicHints", Integer.toString(properties.maxLogicHints())))
                .call()
                .entity(AiLogicIssueResponse.class);

        if (aiResponse == null || aiResponse.issues() == null || aiResponse.issues().isEmpty()) {
            return List.of();
        }

        List<Finding> findings = new ArrayList<>();
        for (AiLogicIssue issue : aiResponse.issues()) {
            if (issue.line() <= 0 || isBlank(issue.title()) || isBlank(issue.summary())) {
                continue;
            }

            findings.add(new Finding(
                    Severity.TIP,
                    issue.line(),
                    clean(issue.title()),
                    clean(issue.summary()),
                    clean(defaultIfBlank(issue.suggestedFix(), "Test this branch with a focused input and compare the output to the expected result.")),
                    List.of(LOGIC_ISSUE_DOC),
                    new AiInsight(
                            clean(issue.summary()),
                            clean(defaultIfBlank(issue.likelyCause(), "The code pattern on this line looks suspicious, but the issue still needs confirmation with a real test case.")),
                            normalizeConfidence(issue.confidence())
                    )
            ));
        }

        findings.sort(Comparator.comparingInt(Finding::line));
        return findings.stream().limit(properties.maxLogicHints()).toList();
    }

    private AnalysisResponse rebuildResponse(AnalysisResponse base, List<Finding> findings) {
        int errors = (int) findings.stream().filter(finding -> finding.severity() == Severity.ERROR).count();
        int warnings = (int) findings.stream().filter(finding -> finding.severity() == Severity.WARNING).count();
        int score = findings.stream().allMatch(finding -> finding.severity() == Severity.TIP)
                ? 90
                : Math.max(0, 100 - (errors * 50 + warnings * 25));

        String summary = displayName(base.level()) + " analysis complete: " + findings.size()
                + " finding(s) across " + base.checksApplied() + " checks.";

        return new AnalysisResponse(summary, score, base.level(), base.checksApplied(), findings);
    }

    private boolean containsOnlyNoIssueTip(List<Finding> findings) {
        return findings.size() == 1
                && findings.getFirst().severity() == Severity.TIP
                && "No obvious issues found".equals(findings.getFirst().title());
    }

    private String findingsJson(List<Finding> findings) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(findings);
        } catch (JsonProcessingException ignored) {
            return "[]";
        }
    }

    private String findingKey(int line, String title) {
        return line + "|" + title.trim().toLowerCase(Locale.ENGLISH);
    }

    private String displayName(String level) {
        if (level == null || level.isBlank()) {
            return "Beginner";
        }
        return Character.toUpperCase(level.charAt(0)) + level.substring(1).toLowerCase(Locale.ENGLISH);
    }

    private String normalizeConfidence(String confidence) {
        if (confidence == null || confidence.isBlank()) {
            return "medium";
        }
        String normalized = confidence.trim().toLowerCase(Locale.ENGLISH);
        if (normalized.equals("high") || normalized.equals("medium") || normalized.equals("low")) {
            return normalized;
        }
        return "medium";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private record AiFindingBatchResponse(List<AiFindingExplanation> insights) {
    }

    private record AiFindingExplanation(int line,
                                        String title,
                                        String summary,
                                        String likelyCause,
                                        String confidence) {
    }

    private record AiLogicIssueResponse(List<AiLogicIssue> issues) {
    }

    private record AiLogicIssue(int line,
                                String title,
                                String summary,
                                String likelyCause,
                                String suggestedFix,
                                String confidence) {
    }
}
