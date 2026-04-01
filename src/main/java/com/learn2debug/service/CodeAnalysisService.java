package com.learn2debug.service;

import com.learn2debug.model.AnalysisResponse;
import com.learn2debug.model.Finding;
import com.learn2debug.model.Severity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CodeAnalysisService {

    public AnalysisResponse analyze(String code, String level) {
        List<Finding> findings = new ArrayList<>();
        String[] lines = code.split("\\R", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String normalized = line.toLowerCase(Locale.ROOT);
            int lineNumber = i + 1;

            if (normalized.contains("/ 0") || normalized.contains("/0")) {
                findings.add(new Finding(
                        Severity.ERROR,
                        lineNumber,
                        "Possible division by zero",
                        "Dividing by zero throws ArithmeticException at runtime.",
                        "Validate denominator values before division.",
                        List.of(
                                "https://docs.oracle.com/javase/8/docs/api/java/lang/ArithmeticException.html",
                                "https://docs.oracle.com/javase/tutorial/java/nutsandbolts/op2.html"
                        )
                ));
            }

            if (normalized.contains("== null")) {
                findings.add(new Finding(
                        Severity.WARNING,
                        lineNumber,
                        "Potential null handling issue",
                        "Null checks may be incomplete or inconsistent with later usage.",
                        "Consider explicit null guarding and safe object access patterns.",
                        List.of(
                                "https://docs.oracle.com/javase/8/docs/api/java/lang/NullPointerException.html",
                                "https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html"
                        )
                ));
            }

            if (normalized.matches(".*\\btemp\\b.*") && (normalized.contains("int ") || normalized.contains("string "))) {
                findings.add(new Finding(
                        Severity.TIP,
                        lineNumber,
                        "Possible temporary variable cleanup",
                        "Temporary variables can reduce readability if not needed.",
                        "Remove unused variables or rename them to reflect purpose.",
                        List.of(
                                "https://checkstyle.sourceforge.io/",
                                "https://spotbugs.readthedocs.io/"
                        )
                ));
            }
        }

        if (findings.isEmpty()) {
            findings.add(new Finding(
                    Severity.TIP,
                    1,
                    "No obvious issues found",
                    "Great start. Static checks did not find common beginner issues in this snippet.",
                    "Add tests and run additional linters for deeper validation.",
                    List.of(
                            "https://junit.org/junit5/docs/current/user-guide/",
                            "https://maven.apache.org/surefire/maven-surefire-plugin/"
                    )
            ));
        }

        int score = Math.max(0, 100 - (errorCount(findings) * 25 + warningCount(findings) * 10));
        String summary = "Analysis complete: " + findings.size() + " finding(s) for level=" + (level == null ? "beginner" : level);

        return new AnalysisResponse(summary, score, findings);
    }

    private int errorCount(List<Finding> findings) {
        return (int) findings.stream().filter(finding -> finding.severity() == Severity.ERROR).count();
    }

    private int warningCount(List<Finding> findings) {
        return (int) findings.stream().filter(finding -> finding.severity() == Severity.WARNING).count();
    }
}
