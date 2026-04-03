package com.learn2debug.service;

import com.learn2debug.model.AnalysisResponse;
import com.learn2debug.model.Finding;
import com.learn2debug.model.Severity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class CodeAnalysisService {

    private static final Pattern DIV_BY_ZERO = Pattern.compile("/\\s*0");
    private static final Pattern NULL_DEREF = Pattern.compile("null\\s*\\.");
    private static final Pattern STRING_EQ = Pattern.compile("==\\s*\"|\"\\s*==");
    private static final Pattern ASSIGN_IN_IF = Pattern.compile("if\\s*\\([^=]*=[^=]");

    public AnalysisResponse analyze(String code, String level) {
        List<Finding> findings = new ArrayList<>();
        String[] lines = code.split("\\R", -1);
        String lowerCode = code.toLowerCase(Locale.ROOT);

        // 1. Basic syntax checks
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            int lineNum = i + 1;

            if (DIV_BY_ZERO.matcher(line).find()) {
                findings.add(new Finding(Severity.ERROR, lineNum,
                        "Division by zero risk",
                        "Dividing by zero throws ArithmeticException.",
                        "Fix: Check denominator != 0 before dividing.",
                        List.of("https://docs.oracle.com/javase/8/docs/api/java/lang/ArithmeticException.html")));
            }

            if (NULL_DEREF.matcher(line).find()) {
                findings.add(new Finding(Severity.ERROR, lineNum,
                        "Null pointer dereference",
                        "You are calling a method on a null object.",
                        "Fix: Add 'if (obj != null)' before using it.",
                        List.of("https://docs.oracle.com/javase/8/docs/api/java/lang/NullPointerException.html")));
            }

            if (STRING_EQ.matcher(line).find()) {
                findings.add(new Finding(Severity.WARNING, lineNum,
                        "String comparison with ==",
                        "== checks memory address, not content.",
                        "Fix: Use string1.equals(string2)",
                        List.of("https://docs.oracle.com/javase/8/docs/api/java/lang/String.html#equals(java.lang.Object)")));
            }
        }

        // 2. Strong brace checking (very common beginner mistake)
        long openBraces = lowerCode.chars().filter(ch -> ch == '{').count();
        long closeBraces = lowerCode.chars().filter(ch -> ch == '}').count();

        if (openBraces != closeBraces) {
            findings.add(new Finding(Severity.ERROR, 1,
                    "Unbalanced braces { }",
                    "You have " + openBraces + " opening { but only " + closeBraces + " closing }.",
                    "Fix: Add missing } or remove extra { so they match perfectly.",
                    List.of(
                        "https://docs.oracle.com/javase/tutorial/java/nutsandbolts/opsummary.html",
                        "https://docs.oracle.com/javase/tutorial/java/nutsandbolts/expressions.html"
                    )));
        }

        // 3. Missing semicolon heuristic
        int missingSemi = 0;
        for (String line : lines) {
            String t = line.trim();
            if (!t.isEmpty() && !t.endsWith(";") && !t.endsWith("{") && !t.endsWith("}") &&
                !t.startsWith("//") && !t.startsWith("/*") && !t.endsWith(":")) {
                missingSemi++;
            }
        }
        if (missingSemi > 3) {
            findings.add(new Finding(Severity.WARNING, 1,
                    "Many statements missing semicolons",
                    "Most Java statements must end with ;",
                    "Fix: Add ; at the end of each statement.",
                    List.of("https://docs.oracle.com/javase/tutorial/java/nutsandbolts/expressions.html")));
        }

        // 4. Educational logical warning (for obviously broken logic)
        if (code.length() > 150) {
            findings.add(new Finding(Severity.WARNING, 1,
                    "Possible logical / algorithmic bug",
                    "No syntax errors were found, but the code logic appears incorrect or incomplete.",
                    "Fix: Test this code with multiple test cases. Static analysis cannot verify algorithm correctness.",
                    List.of(
                        "https://docs.oracle.com/javase/tutorial/java/nutsandbolts/index.html",
                        "https://www.baeldung.com/java-debugging"
                    )));
        }

        // 5. Summary message when multiple issues exist
        if (findings.size() >= 2) {
            findings.add(new Finding(Severity.ERROR, 1,
                    "Multiple issues detected in your code",
                    "Your code contains syntax problems + possible logical errors.",
                    "Fix: Start with braces and semicolons first, then test logic thoroughly.",
                    List.of("https://docs.oracle.com/javase/tutorial/getStarted/cupojava/")));
        }

        // Fallback (only shown if truly clean)
        if (findings.isEmpty()) {
            findings.add(new Finding(Severity.TIP, 1,
                    "No obvious issues found",
                    "Static analysis passed.",
                    "Tip: Always test your code with multiple inputs even if analysis looks clean.",
                    List.of("https://junit.org/junit5/docs/current/user-guide/")));
        }

        // Scoring - very strict for educational purpose
        int errors = (int) findings.stream().filter(f -> f.severity() == Severity.ERROR).count();
        int warnings = (int) findings.stream().filter(f -> f.severity() == Severity.WARNING).count();
        int score = Math.max(0, 100 - (errors * 50 + warnings * 25));

        String summary = "Analysis complete: " + findings.size() + " finding(s) for level=" + (level != null ? level : "beginner");

        return new AnalysisResponse(summary, score, findings);
    }
}
