package com.learn2debug.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ParseProblemException;

import com.learn2debug.model.AnalysisResponse;
import com.learn2debug.model.Finding;
import com.learn2debug.model.Severity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CodeAnalysisService {

    public AnalysisResponse analyze(String code, String level) {
        List<Finding> findings = new ArrayList<>();

        try {
            // Real Java parsing - this is the super weapon
            CompilationUnit cu = StaticJavaParser.parse(code);

            // Division by zero
            cu.findAll(BinaryExpr.class).forEach(expr -> {
                if (expr.getOperator() == BinaryExpr.Operator.DIVIDE &&
                    expr.getRight().isIntegerLiteralExpr() &&
                    expr.getRight().asIntegerLiteralExpr().asNumber().intValue() == 0) {
                    findings.add(new Finding(Severity.ERROR, expr.getBegin().get().line,
                            "Division by zero",
                            "You are dividing by zero — this will crash at runtime.",
                            "Fix: Add a check like `if (denominator != 0)` before dividing.",
                            List.of("https://docs.oracle.com/javase/8/docs/api/java/lang/ArithmeticException.html")));
                }
            });

            // Null pointer dereference
            cu.findAll(MethodCallExpr.class).forEach(call -> {
                if (call.getScope().isPresent() && call.getScope().get().isNullLiteralExpr()) {
                    findings.add(new Finding(Severity.ERROR, call.getBegin().get().line,
                            "Null pointer dereference",
                            "You are calling a method on null.",
                            "Fix: Add null check: `if (obj != null)` before calling.",
                            List.of("https://docs.oracle.com/javase/8/docs/api/java/lang/NullPointerException.html")));
                }
            });

            // Assignment in if condition
            cu.findAll(IfStmt.class).forEach(ifStmt -> {
                if (ifStmt.getCondition().toString().contains("=") && !ifStmt.getCondition().toString().contains("==")) {
                    findings.add(new Finding(Severity.WARNING, ifStmt.getBegin().get().line,
                            "Assignment in if condition",
                            "You probably meant == (compare) but wrote = (assign).",
                            "Fix: Change = to ==",
                            List.of("https://docs.oracle.com/javase/tutorial/java/nutsandbolts/if.html")));
                }
            });

        } catch (ParseProblemException e) {
            // Real syntax errors (missing semicolon, unbalanced braces, etc.)
            findings.add(new Finding(Severity.ERROR, 1,
                    "Syntax error",
                    "Your code has syntax problems (missing semicolon, unbalanced braces, or typos).",
                    "Fix: Check every statement ends with ; and braces match perfectly.",
                    List.of("https://docs.oracle.com/javase/tutorial/getStarted/cupojava/")));
        }

        // Fallback
        if (findings.isEmpty()) {
            findings.add(new Finding(Severity.TIP, 1,
                    "No obvious issues found",
                    "Static analysis passed — great job!",
                    "Tip: Even clean code can have logical bugs. Always test with real inputs.",
                    List.of("https://junit.org/junit5/docs/current/user-guide/")));
        }

        int errors = (int) findings.stream().filter(f -> f.severity() == Severity.ERROR).count();
        int warnings = (int) findings.stream().filter(f -> f.severity() == Severity.WARNING).count();
        int score = Math.max(0, 100 - (errors * 50 + warnings * 25));

        String summary = "Analysis complete: " + findings.size() + " finding(s) for level=" + (level != null ? level : "beginner");

        return new AnalysisResponse(summary, score, findings);
    }
}
