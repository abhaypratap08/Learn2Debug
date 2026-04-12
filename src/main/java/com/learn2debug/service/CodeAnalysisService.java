package com.learn2debug.service;

import com.sun.source.util.JavacTask;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.learn2debug.model.AnalysisResponse;
import com.learn2debug.model.Finding;
import com.learn2debug.model.Severity;
import org.springframework.stereotype.Service;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CodeAnalysisService {
    private static final Pattern LINE_PATTERN = Pattern.compile("line (\\d+)");
    private static final Pattern TOP_LEVEL_TYPE_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:public\\s+|protected\\s+|private\\s+|abstract\\s+|final\\s+|sealed\\s+|non-sealed\\s+|strictfp\\s+)*\\b(class|interface|enum|record)\\b"
    );
    private static final Pattern PUBLIC_TYPE_NAME_PATTERN = Pattern.compile(
            "(?m)\\bpublic\\s+(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b"
    );
    private static final Pattern CANNOT_FIND_SYMBOL_PATTERN = Pattern.compile(
            "cannot find symbol symbol: (?:(class|variable|method|constructor|interface|enum|record) )?(.+?)(?: location: (.+))?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PACKAGE_NOT_FOUND_PATTERN = Pattern.compile(
            "package (.+?) does not exist",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern INCOMPATIBLE_TYPES_PATTERN = Pattern.compile(
            "incompatible types: (.+?) cannot be converted to (.+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> RAW_COLLECTION_TYPES = Set.of(
            "Collection",
            "List",
            "ArrayList",
            "LinkedList",
            "Set",
            "HashSet",
            "Map",
            "HashMap",
            "Queue",
            "Deque"
    );
    private static final List<String> COMMON_CONTEXT_IMPORTS = List.of(
            "import java.io.*;",
            "import java.math.*;",
            "import java.nio.file.*;",
            "import java.time.*;",
            "import java.util.*;",
            "import java.util.concurrent.*;",
            "import java.util.function.*;",
            "import java.util.regex.*;",
            "import java.util.stream.*;"
    );
    private static final Map<String, String> COMMON_IMPORTS = Map.ofEntries(
            Map.entry("ArrayDeque", "java.util.ArrayDeque"),
            Map.entry("ArrayList", "java.util.ArrayList"),
            Map.entry("Arrays", "java.util.Arrays"),
            Map.entry("BigDecimal", "java.math.BigDecimal"),
            Map.entry("BigInteger", "java.math.BigInteger"),
            Map.entry("BufferedReader", "java.io.BufferedReader"),
            Map.entry("BufferedWriter", "java.io.BufferedWriter"),
            Map.entry("Collectors", "java.util.stream.Collectors"),
            Map.entry("Comparator", "java.util.Comparator"),
            Map.entry("Collections", "java.util.Collections"),
            Map.entry("Deque", "java.util.Deque"),
            Map.entry("Files", "java.nio.file.Files"),
            Map.entry("HashMap", "java.util.HashMap"),
            Map.entry("HashSet", "java.util.HashSet"),
            Map.entry("InputStreamReader", "java.io.InputStreamReader"),
            Map.entry("IOException", "java.io.IOException"),
            Map.entry("IntStream", "java.util.stream.IntStream"),
            Map.entry("LinkedList", "java.util.LinkedList"),
            Map.entry("List", "java.util.List"),
            Map.entry("Matcher", "java.util.regex.Matcher"),
            Map.entry("Map", "java.util.Map"),
            Map.entry("Pattern", "java.util.regex.Pattern"),
            Map.entry("Paths", "java.nio.file.Paths"),
            Map.entry("PriorityQueue", "java.util.PriorityQueue"),
            Map.entry("Queue", "java.util.Queue"),
            Map.entry("Scanner", "java.util.Scanner"),
            Map.entry("Set", "java.util.Set"),
            Map.entry("Stream", "java.util.stream.Stream"),
            Map.entry("StringTokenizer", "java.util.StringTokenizer")
    );
    private final Supplier<JavaCompiler> compilerSupplier;

    public CodeAnalysisService() {
        this(ToolProvider::getSystemJavaCompiler);
    }

    CodeAnalysisService(Supplier<JavaCompiler> compilerSupplier) {
        this.compilerSupplier = compilerSupplier;
    }

    public AnalysisResponse analyze(String code, String requestedLevel) {
        AnalysisLevel level = AnalysisLevel.from(requestedLevel);
        List<Finding> findings = new ArrayList<>();
        JavaCompiler compiler = compilerSupplier.get();
        boolean compilerAvailable = compiler != null;
        List<Finding> compilerFindings = compilerAvailable ? collectCompilerFindings(code, compiler) : List.of();
        findings.addAll(compilerFindings);

        try {
            ParsedCode parsedCode = parseCode(code);
            CompilationUnit compilationUnit = parsedCode.compilationUnit();
            Set<String> stringVariables = collectStringVariables(compilationUnit);

            analyzeExecutableBodies(compilationUnit, parsedCode.lineOffset(), findings);
            analyzeStringComparisons(compilationUnit, parsedCode.lineOffset(), stringVariables, findings);
            analyzeConditionAssignments(compilationUnit, parsedCode.lineOffset(), findings);
            analyzeEmptyControlStatements(compilationUnit, parsedCode.lineOffset(), findings);

            if (level.includes(AnalysisLevel.INTERMEDIATE)) {
                analyzeConstantConditions(compilationUnit, parsedCode.lineOffset(), findings);
                analyzeRawCollectionTypes(compilationUnit, parsedCode.lineOffset(), findings);
                analyzeUnusedLocalVariables(compilationUnit, parsedCode.lineOffset(), findings);
            }

            if (level.includes(AnalysisLevel.ADVANCED)) {
                analyzeCatchClauses(compilationUnit, parsedCode.lineOffset(), findings);
                analyzeMutablePublicFields(compilationUnit, parsedCode.lineOffset(), findings);
            }
        } catch (SnippetParseException e) {
            if (compilerFindings.isEmpty()) {
                findings.add(buildSyntaxFinding(code, e.exception(), e.lineOffset(), compiler));
            }
        } catch (ParseProblemException e) {
            if (compilerFindings.isEmpty()) {
                findings.add(buildSyntaxFinding(code, e, 0, compiler));
            }
        }

        if (findings.isEmpty()) {
            findings.add(compilerAvailable
                    ? noIssuesFinding(level)
                    : compilerUnavailableFinding());
        }

        findings.sort(Comparator.comparingInt(Finding::line).thenComparing(this::severityWeight));

        int errors = (int) findings.stream().filter(finding -> finding.severity() == Severity.ERROR).count();
        int warnings = (int) findings.stream().filter(finding -> finding.severity() == Severity.WARNING).count();
        int score = findings.stream().allMatch(finding -> finding.severity() == Severity.TIP)
                ? 90
                : Math.max(0, 100 - (errors * 50 + warnings * 25));

        String summary = level.displayName() + " analysis complete: " + findings.size()
                + " finding(s) across " + level.checksApplied() + " checks.";

        return new AnalysisResponse(summary, score, level.value(), level.checksApplied(), findings);
    }

    private Set<String> collectStringVariables(CompilationUnit compilationUnit) {
        Set<String> stringVariables = new HashSet<>();

        compilationUnit.findAll(VariableDeclarator.class).forEach(variable -> {
            if ("String".equals(variable.getType().asString())) {
                stringVariables.add(variable.getNameAsString());
            }
        });

        compilationUnit.findAll(Parameter.class).forEach(parameter -> {
            if ("String".equals(parameter.getType().asString())) {
                stringVariables.add(parameter.getNameAsString());
            }
        });

        return stringVariables;
    }

    private void analyzeExecutableBodies(CompilationUnit compilationUnit, int lineOffset, List<Finding> findings) {
        compilationUnit.findAll(MethodDeclaration.class).forEach(method ->
                method.getBody().ifPresent(body -> analyzeBlock(body, lineOffset, new HashMap<>(), new HashMap<>(), findings)));

        compilationUnit.findAll(ConstructorDeclaration.class).forEach(constructor ->
                analyzeBlock(constructor.getBody(), lineOffset, new HashMap<>(), new HashMap<>(), findings));
    }

    private void analyzeStringComparisons(CompilationUnit compilationUnit,
                                          int lineOffset,
                                          Set<String> stringVariables,
                                          List<Finding> findings) {
        compilationUnit.findAll(BinaryExpr.class).forEach(expression -> {
            if (!isStringReferenceComparison(expression, stringVariables)) {
                return;
            }

            String operatorText = expression.getOperator() == BinaryExpr.Operator.EQUALS ? "==" : "!=";
            findings.add(new Finding(
                    Severity.WARNING,
                    lineOf(expression, lineOffset),
                    "String comparison with " + operatorText,
                    "Using " + operatorText + " compares object references, not String contents, so this check can behave unexpectedly.",
                    "Use .equals(...) for value comparison, or invert it with !\"value\".equals(text) when checking inequality.",
                    List.of("https://docs.oracle.com/javase/tutorial/java/data/strings.html")
            ));
        });
    }

    private void analyzeConditionAssignments(CompilationUnit compilationUnit, int lineOffset, List<Finding> findings) {
        compilationUnit.findAll(IfStmt.class).forEach(ifStmt ->
                addAssignmentConditionFinding(ifStmt.getCondition(), lineOffset, "if", findings));
        compilationUnit.findAll(WhileStmt.class).forEach(whileStmt ->
                addAssignmentConditionFinding(whileStmt.getCondition(), lineOffset, "while", findings));
        compilationUnit.findAll(DoStmt.class).forEach(doStmt ->
                addAssignmentConditionFinding(doStmt.getCondition(), lineOffset, "do-while", findings));
        compilationUnit.findAll(ForStmt.class).forEach(forStmt ->
                forStmt.getCompare().ifPresent(compare ->
                        addAssignmentConditionFinding(compare, lineOffset, "for", findings)));
    }

    private void analyzeEmptyControlStatements(CompilationUnit compilationUnit, int lineOffset, List<Finding> findings) {
        compilationUnit.findAll(IfStmt.class).forEach(ifStmt ->
                addEmptyControlFinding(ifStmt.getThenStmt(), lineOffset, "if", findings));
        compilationUnit.findAll(IfStmt.class).forEach(ifStmt ->
                ifStmt.getElseStmt().ifPresent(elseStmt -> addEmptyControlFinding(elseStmt, lineOffset, "else", findings)));
        compilationUnit.findAll(WhileStmt.class).forEach(whileStmt ->
                addEmptyControlFinding(whileStmt.getBody(), lineOffset, "while", findings));
        compilationUnit.findAll(DoStmt.class).forEach(doStmt ->
                addEmptyControlFinding(doStmt.getBody(), lineOffset, "do-while", findings));
        compilationUnit.findAll(ForStmt.class).forEach(forStmt ->
                addEmptyControlFinding(forStmt.getBody(), lineOffset, "for", findings));
        compilationUnit.findAll(ForEachStmt.class).forEach(forEachStmt ->
                addEmptyControlFinding(forEachStmt.getBody(), lineOffset, "for-each", findings));
    }

    private void analyzeConstantConditions(CompilationUnit compilationUnit, int lineOffset, List<Finding> findings) {
        compilationUnit.findAll(IfStmt.class).forEach(ifStmt ->
                addConstantConditionFinding(ifStmt.getCondition(), lineOffset, "if", findings));
        compilationUnit.findAll(WhileStmt.class).forEach(whileStmt ->
                addConstantConditionFinding(whileStmt.getCondition(), lineOffset, "while", findings));
        compilationUnit.findAll(DoStmt.class).forEach(doStmt ->
                addConstantConditionFinding(doStmt.getCondition(), lineOffset, "do-while", findings));
        compilationUnit.findAll(ForStmt.class).forEach(forStmt ->
                forStmt.getCompare().ifPresent(compare ->
                        addConstantConditionFinding(compare, lineOffset, "for", findings)));
    }

    private void analyzeRawCollectionTypes(CompilationUnit compilationUnit, int lineOffset, List<Finding> findings) {
        compilationUnit.findAll(VariableDeclarator.class).forEach(variable -> {
            if (!isRawCollectionType(variable.getType())) {
                return;
            }

            findings.add(new Finding(
                    Severity.WARNING,
                    lineOf(variable, lineOffset),
                    "Raw collection type",
                    "This collection is missing a generic type, which loses type safety and can hide ClassCastException problems until runtime.",
                    "Specify the element type, for example `List<String> names = new ArrayList<>();`.",
                    List.of("https://docs.oracle.com/javase/tutorial/extra/generics/")
            ));
        });
    }

    private void analyzeUnusedLocalVariables(CompilationUnit compilationUnit, int lineOffset, List<Finding> findings) {
        compilationUnit.findAll(MethodDeclaration.class).forEach(method ->
                method.getBody().ifPresent(body -> findUnusedLocalVariables(body, lineOffset, findings)));
        compilationUnit.findAll(ConstructorDeclaration.class).forEach(constructor ->
                findUnusedLocalVariables(constructor.getBody(), lineOffset, findings));
    }

    private void analyzeCatchClauses(CompilationUnit compilationUnit, int lineOffset, List<Finding> findings) {
        compilationUnit.findAll(CatchClause.class).forEach(catchClause -> {
            String caughtType = catchClause.getParameter().getType().asString();
            if (isBroadCatchType(caughtType)) {
                findings.add(new Finding(
                        Severity.WARNING,
                        lineOf(catchClause, lineOffset),
                        "Overly broad catch clause",
                        "Catching " + caughtType + " makes it harder to understand which failures you expect and can hide programming mistakes.",
                        "Catch the most specific exception type you can handle, or let unexpected exceptions surface.",
                        List.of("https://docs.oracle.com/javase/tutorial/essential/exceptions/catchOrDeclare.html")
                ));
            }

            if (catchClause.getBody().getStatements().isEmpty()) {
                findings.add(new Finding(
                        Severity.WARNING,
                        lineOf(catchClause, lineOffset),
                        "Empty catch block",
                        "This catch block swallows the exception completely, which makes debugging much harder when something fails.",
                        "Log the exception, rethrow it, or handle it with a concrete recovery step.",
                        List.of("https://docs.oracle.com/javase/tutorial/essential/exceptions/try.html")
                ));
            }
        });
    }

    private void analyzeMutablePublicFields(CompilationUnit compilationUnit, int lineOffset, List<Finding> findings) {
        compilationUnit.findAll(FieldDeclaration.class).forEach(field -> {
            if (!field.isPublic() || field.isFinal()) {
                return;
            }

            field.getVariables().forEach(variable -> findings.add(new Finding(
                    Severity.WARNING,
                    lineOf(variable, lineOffset),
                    "Mutable public field",
                    "Public mutable fields expose object state directly, which makes invariants harder to protect and refactors riskier.",
                    "Prefer private fields with methods that control how the value is read or changed.",
                    List.of("https://docs.oracle.com/javase/tutorial/java/javaOO/accesscontrol.html")
            )));
        });
    }

    private void findUnusedLocalVariables(BlockStmt body, int lineOffset, List<Finding> findings) {
        body.findAll(VariableDeclarator.class).forEach(variable -> {
            if (!isLocalVariable(variable)) {
                return;
            }

            boolean used = body.findAll(NameExpr.class).stream()
                    .anyMatch(nameExpr -> nameExpr.getNameAsString().equals(variable.getNameAsString())
                            && !nameExpr.findAncestor(VariableDeclarator.class)
                            .map(ancestor -> ancestor == variable)
                            .orElse(false));

            if (!used) {
                findings.add(new Finding(
                        Severity.WARNING,
                        lineOf(variable, lineOffset),
                        "Unused local variable",
                        "This local variable is declared but never read, which usually means leftover code or a missing use of the value.",
                        "Remove the variable if it is unnecessary, or use it where the result is actually needed.",
                        List.of("https://docs.oracle.com/javase/tutorial/java/nutsandbolts/variables.html")
                ));
            }
        });
    }

    private ParsedCode parseCode(String code) {
        SourceVariant wrappedVariant = wrappedSourceVariant(code);

        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(code);
            boolean compactSnippet = compilationUnit.getTypes().size() == 1
                    && "$COMPACT_CLASS".equals(compilationUnit.getType(0).getNameAsString());

            if (!compilationUnit.getTypes().isEmpty() && !compactSnippet) {
                return new ParsedCode(compilationUnit, 0);
            }

            return new ParsedCode(StaticJavaParser.parse(wrappedVariant.source()), wrappedVariant.lineOffset());
        } catch (ParseProblemException ignored) {
            try {
                return new ParsedCode(StaticJavaParser.parse(wrappedVariant.source()), wrappedVariant.lineOffset());
            } catch (ParseProblemException wrappedException) {
                throw new SnippetParseException(wrappedException, wrappedVariant.lineOffset());
            }
        }
    }

    private Finding buildSyntaxFinding(String code,
                                       ParseProblemException fallbackException,
                                       int lineOffset,
                                       JavaCompiler compiler) {
        CompilerDiagnosticDetail compilerDiagnostic = compiler == null ? null : findFirstCompilerDiagnostic(code, compiler);
        if (compilerDiagnostic != null) {
            return new Finding(
                    Severity.ERROR,
                    compilerDiagnostic.line(),
                    compilerFindingTitle(compilerDiagnostic.message()),
                    compilerFindingExplanation(compilerDiagnostic.message(), code, compilerDiagnostic.line()),
                    syntaxFixSuggestion(compilerDiagnostic.message()),
                    List.of("https://docs.oracle.com/javase/tutorial/getStarted/cupojava/")
            );
        }

        return new Finding(
                Severity.ERROR,
                extractSyntaxErrorLine(fallbackException, lineOffset),
                "Syntax error",
                "The code could not be parsed as Java. This usually means a missing semicolon, unmatched braces, or an invalid statement.",
                "Check the highlighted line first for missing semicolons, missing braces, or malformed conditions, then try again.",
                List.of("https://docs.oracle.com/javase/tutorial/getStarted/cupojava/")
        );
    }

    private Finding noIssuesFinding(AnalysisLevel level) {
        return new Finding(
                Severity.TIP,
                1,
                "No obvious issues found",
                "No obvious " + level.value() + "-level issues were detected by the current checks.",
                "Keep testing with real inputs because this tool is still a static analyzer, not a full compiler or runtime.",
                List.of("https://docs.junit.org/current/user-guide/")
        );
    }

    private Finding compilerUnavailableFinding() {
        return new Finding(
                Severity.WARNING,
                1,
                "Compiler unavailable",
                "This Learn2Debug backend is running without javac, so compile-time errors like missing symbols and type mismatches cannot be verified right now.",
                "Run the backend on a JDK 21 runtime instead of a JRE, then redeploy or restart the API so compiler-backed analysis is available.",
                List.of("https://docs.oracle.com/en/java/javase/21/docs/api/java.compiler/javax/tools/ToolProvider.html")
        );
    }

    private List<Finding> collectCompilerFindings(String code, JavaCompiler compiler) {
        return collectCompilerFindingsForVariant(code, compilerSourceVariant(code), compiler);
    }

    private CompilerDiagnosticDetail findFirstCompilerDiagnostic(String code, JavaCompiler compiler) {
        List<CompilerDiagnosticDetail> diagnostics = collectCompilerDiagnosticsWithFallback(code, compilerSourceVariant(code), compiler);
        return diagnostics.isEmpty() ? null : diagnostics.getFirst();
    }

    private List<Finding> collectCompilerFindingsForVariant(String code, SourceVariant sourceVariant, JavaCompiler compiler) {
        List<CompilerDiagnosticDetail> diagnostics = collectCompilerDiagnosticsWithFallback(code, sourceVariant, compiler);
        if (diagnostics.isEmpty()) {
            return List.of();
        }

        List<Finding> findings = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        for (CompilerDiagnosticDetail diagnostic : diagnostics) {
            String key = diagnostic.line() + "|" + diagnostic.message();
            if (!seen.add(key)) {
                continue;
            }

            findings.add(new Finding(
                    Severity.ERROR,
                    diagnostic.line(),
                    compilerFindingTitle(diagnostic.message()),
                    compilerFindingExplanation(diagnostic.message(), code, diagnostic.line()),
                    compilerFixSuggestion(diagnostic.message(), code, diagnostic.line()),
                    List.of("https://docs.oracle.com/javase/tutorial/getStarted/cupojava/")
            ));

            if (findings.size() >= 5) {
                break;
            }
        }

        return findings;
    }

    private List<CompilerDiagnosticDetail> collectCompilerDiagnosticsWithFallback(String code,
                                                                                 SourceVariant sourceVariant,
                                                                                 JavaCompiler compiler) {
        List<CompilerDiagnosticDetail> diagnostics = collectCompilerDiagnosticsForVariant(sourceVariant, compiler);
        if (diagnostics.isEmpty()) {
            return diagnostics;
        }

        SourceVariant contextualVariant = contextualImportsSourceVariant(code);
        if (contextualVariant == null) {
            return diagnostics;
        }

        List<CompilerDiagnosticDetail> fallbackDiagnostics = collectCompilerDiagnosticsForVariant(contextualVariant, compiler);
        if (fallbackDiagnostics.size() < diagnostics.size()) {
            return fallbackDiagnostics;
        }

        return diagnostics;
    }

    private List<CompilerDiagnosticDetail> collectCompilerDiagnosticsForVariant(SourceVariant sourceVariant, JavaCompiler compiler) {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Path outputDirectory = null;
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics,
                Locale.ENGLISH,
                StandardCharsets.UTF_8
        )) {
            outputDirectory = Files.createTempDirectory("learn2debug-javac-");
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(outputDirectory));

            JavaFileObject sourceFile = new InMemoryJavaSource(sourceVariant.fileName(), sourceVariant.source());
            JavacTask task = (JavacTask) compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    List.of("-proc:none", "-Xlint:none", "-implicit:none"),
                    null,
                    List.of(sourceFile)
            );
            task.call();

            List<CompilerDiagnosticDetail> compilerDiagnostics = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() != Diagnostic.Kind.ERROR || diagnostic.getLineNumber() <= 0) {
                    continue;
                }

                int line = Math.max(1, (int) diagnostic.getLineNumber() - sourceVariant.lineOffset());
                compilerDiagnostics.add(new CompilerDiagnosticDetail(line, normalizeCompilerMessage(diagnostic.getMessage(Locale.ENGLISH))));
            }
            return compilerDiagnostics;
        } catch (IOException | RuntimeException ignored) {
            return List.of();
        } finally {
            deleteDirectoryQuietly(outputDirectory);
        }
    }

    private SourceVariant wrappedSourceVariant(String code) {
        HeaderSplit headerSplit = splitLeadingHeader(code);
        StringBuilder source = new StringBuilder();

        if (!headerSplit.header().isBlank()) {
            source.append(headerSplit.header());
            if (!headerSplit.header().endsWith("\n")) {
                source.append('\n');
            }
        }

        source.append("class SnippetWrapper {\n");
        source.append("    void run() {\n");
        source.append(headerSplit.body());
        if (!headerSplit.body().endsWith("\n")) {
            source.append('\n');
        }
        source.append("    }\n");
        source.append("}\n");

        return new SourceVariant(source.toString(), 2, "SnippetWrapper.java");
    }

    private HeaderSplit splitLeadingHeader(String code) {
        String[] lines = code.split("\\R", -1);
        StringBuilder header = new StringBuilder();
        StringBuilder body = new StringBuilder();
        boolean inHeader = true;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            boolean headerLine = trimmed.isEmpty()
                    || trimmed.startsWith("package ")
                    || trimmed.startsWith("import ")
                    || trimmed.startsWith("//")
                    || trimmed.startsWith("/*")
                    || trimmed.startsWith("*")
                    || trimmed.startsWith("*/");

            if (inHeader && headerLine) {
                header.append(line);
                if (i < lines.length - 1) {
                    header.append('\n');
                }
                continue;
            }

            inHeader = false;
            body.append(line);
            if (i < lines.length - 1) {
                body.append('\n');
            }
        }

        return new HeaderSplit(header.toString(), body.toString());
    }

    private boolean looksLikeCompilationUnit(String code) {
        return TOP_LEVEL_TYPE_PATTERN.matcher(code).find();
    }

    private SourceVariant compilerSourceVariant(String code) {
        if (looksLikeCompilationUnit(code)) {
            return new SourceVariant(code, 0, directSourceFileName(code));
        }
        return wrappedSourceVariant(code);
    }

    private SourceVariant contextualImportsSourceVariant(String code) {
        if (!supportsContextualImports(code)) {
            return null;
        }

        HeaderSplit headerSplit = splitLeadingHeader(code);
        StringBuilder source = new StringBuilder();
        int addedLines = 0;

        if (!headerSplit.header().isBlank()) {
            source.append(headerSplit.header());
            if (!headerSplit.header().endsWith("\n")) {
                source.append('\n');
            }
        }

        addedLines += appendContextImports(source);

        if (looksLikeCompilationUnit(code)) {
            source.append(headerSplit.body());
            if (!headerSplit.body().endsWith("\n")) {
                source.append('\n');
            }
            return new SourceVariant(source.toString(), addedLines, directSourceFileName(code));
        }

        source.append("class SnippetWrapper {\n");
        source.append("    void run() {\n");
        source.append(headerSplit.body());
        if (!headerSplit.body().endsWith("\n")) {
            source.append('\n');
        }
        source.append("    }\n");
        source.append("}\n");

        return new SourceVariant(source.toString(), addedLines + 2, "SnippetWrapper.java");
    }

    private String directSourceFileName(String code) {
        Matcher matcher = PUBLIC_TYPE_NAME_PATTERN.matcher(code);
        if (matcher.find()) {
            return matcher.group(1) + ".java";
        }
        return "SnippetInput.java";
    }

    private int appendContextImports(StringBuilder source) {
        for (String importLine : COMMON_CONTEXT_IMPORTS) {
            source.append(importLine).append('\n');
        }
        return COMMON_CONTEXT_IMPORTS.size();
    }

    private boolean supportsContextualImports(String code) {
        if (hasExplicitPackageOrImport(code)) {
            return false;
        }

        return true;
    }

    private boolean hasExplicitPackageOrImport(String code) {
        return code.lines().map(String::trim).anyMatch(line ->
                line.startsWith("package ") || line.startsWith("import "));
    }

    private String compilerFindingTitle(String compilerMessage) {
        String normalized = compilerMessage.toLowerCase(Locale.ENGLISH);

        if (isSyntaxCompilerMessage(normalized)) {
            return "Syntax error";
        }
        if (normalized.contains("cannot find symbol")) {
            return "Cannot find symbol";
        }
        if (normalized.contains("incompatible types")) {
            return "Incompatible types";
        }
        if (normalized.contains("missing return statement")) {
            return "Missing return statement";
        }
        if (normalized.contains("unreachable statement")) {
            return "Unreachable statement";
        }
        if (normalized.contains("package ") && normalized.contains(" does not exist")) {
            return "Missing package or dependency";
        }
        return "Compiler error";
    }

    private String compilerFindingExplanation(String compilerMessage, String code, int line) {
        String normalized = compilerMessage.toLowerCase(Locale.ENGLISH);
        String lineReference = lineReference(code, line);

        if (isSyntaxCompilerMessage(normalized)) {
            return "The Java compiler reported a syntax error on " + lineReference + ": " + ensureSentence(compilerMessage);
        }

        CannotFindSymbolDetail missingSymbol = parseCannotFindSymbol(compilerMessage);
        if (missingSymbol != null) {
            return missingSymbolExplanation(missingSymbol, lineReference);
        }

        Matcher incompatibleTypesMatcher = INCOMPATIBLE_TYPES_PATTERN.matcher(compilerMessage);
        if (incompatibleTypesMatcher.find()) {
            String fromType = incompatibleTypesMatcher.group(1).trim();
            String toType = incompatibleTypesMatcher.group(2).trim();
            return "The Java compiler stops on " + lineReference + " because a value of type "
                    + fromType + " cannot be used where " + toType + " is required.";
        }

        Matcher packageMatcher = PACKAGE_NOT_FOUND_PATTERN.matcher(compilerMessage);
        if (packageMatcher.find()) {
            return "The Java compiler stops on " + lineReference + " because the package `"
                    + packageMatcher.group(1).trim() + "` is not available to this code.";
        }

        return "The Java compiler reported a compilation error on " + lineReference + ": "
                + ensureSentence(compilerMessage);
    }

    private String compilerFixSuggestion(String compilerMessage, String code, int line) {
        String normalized = compilerMessage.toLowerCase(Locale.ENGLISH);

        if (isSyntaxCompilerMessage(normalized)) {
            return syntaxFixSuggestion(compilerMessage);
        }

        CannotFindSymbolDetail missingSymbol = parseCannotFindSymbol(compilerMessage);
        if (missingSymbol != null) {
            return missingSymbolFixSuggestion(missingSymbol, code, line);
        }

        Matcher incompatibleTypesMatcher = INCOMPATIBLE_TYPES_PATTERN.matcher(compilerMessage);
        if (incompatibleTypesMatcher.find()) {
            String fromType = incompatibleTypesMatcher.group(1).trim();
            String toType = incompatibleTypesMatcher.group(2).trim();
            return "Change the expression on this line so it produces " + toType
                    + ", or change the target type if " + fromType + " is actually what you want.";
        }
        if (normalized.contains("missing return statement")) {
            return "Return a value that matches the method's declared return type on every code path.";
        }
        if (normalized.contains("unreachable statement")) {
            return "Remove the dead statement or move it before the return, throw, break, or continue that blocks it.";
        }
        if (normalized.contains("package ") && normalized.contains(" does not exist")) {
            return "Verify the import path and make sure the dependency or package is actually available to the compiler.";
        }

        return "Fix the compiler-reported error on this line first, then rerun the analysis.";
    }

    private CannotFindSymbolDetail parseCannotFindSymbol(String compilerMessage) {
        Matcher matcher = CANNOT_FIND_SYMBOL_PATTERN.matcher(compilerMessage);
        if (!matcher.find()) {
            return null;
        }

        String kind = matcher.group(1) == null ? "symbol" : matcher.group(1).trim().toLowerCase(Locale.ENGLISH);
        String description = matcher.group(2).trim();
        String location = matcher.group(3) == null ? "" : matcher.group(3).trim();
        return new CannotFindSymbolDetail(kind, description, location);
    }

    private String missingSymbolExplanation(CannotFindSymbolDetail missingSymbol, String lineReference) {
        String displayName = missingSymbol.displayName();
        if (isLikelyTypeSymbol(missingSymbol, displayName)) {
            return "The Java compiler stops on " + lineReference + " because the type `" + displayName
                    + "` is not known here. That usually means a missing import or a misspelled class name.";
        }

        return switch (missingSymbol.kind()) {
            case "class", "interface", "enum", "record" ->
                    "The Java compiler stops on " + lineReference + " because the type `" + displayName
                            + "` is not known here. That usually means a missing import or a misspelled class name.";
            case "method", "constructor" ->
                    "The Java compiler stops on " + lineReference + " because it cannot resolve the "
                            + missingSymbol.kind() + " `" + displayName + "`"
                            + locationSuffix(missingSymbol.location()) + ".";
            case "variable" ->
                    "The Java compiler stops on " + lineReference + " because the variable `" + displayName
                            + "` is not declared in this scope.";
            default ->
                    "The Java compiler stops on " + lineReference + " because it cannot resolve `"
                            + displayName + "`" + locationSuffix(missingSymbol.location()) + ".";
        };
    }

    private String missingSymbolFixSuggestion(CannotFindSymbolDetail missingSymbol, String code, int line) {
        String displayName = missingSymbol.displayName();

        if (isLikelyTypeSymbol(missingSymbol, displayName)) {
            String commonImport = COMMON_IMPORTS.get(displayName);
            if (commonImport != null) {
                return "Add `import " + commonImport + ";` at the top of the file, or use the fully qualified name on "
                        + lineReference(code, line) + ".";
            }
            return "Declare the type `" + displayName + "` or import the package that defines it before using it here.";
        }

        if (Set.of("method", "constructor").contains(missingSymbol.kind())) {
            return "Declare `" + missingSymbol.description() + "`" + locationSuffix(missingSymbol.location())
                    + ", or call the correct existing method name instead.";
        }

        if ("variable".equals(missingSymbol.kind())) {
            return "Declare `" + displayName + "` before this line, or replace it with the correct variable that is already in scope.";
        }

        return "Declare or import `" + displayName + "` before using it on " + lineReference(code, line) + ".";
    }

    private boolean isLikelyTypeSymbol(CannotFindSymbolDetail missingSymbol, String displayName) {
        if (Set.of("class", "interface", "enum", "record").contains(missingSymbol.kind())) {
            return true;
        }

        if ("variable".equals(missingSymbol.kind()) && COMMON_IMPORTS.containsKey(displayName)) {
            return true;
        }

        return !displayName.isBlank() && Character.isUpperCase(displayName.charAt(0))
                && !"this".equals(displayName);
    }

    private String locationSuffix(String location) {
        if (location == null || location.isBlank()) {
            return "";
        }
        return " in " + location;
    }

    private String lineReference(String code, int line) {
        String sourceLine = sourceLine(code, line);
        if (sourceLine == null) {
            return "line " + line;
        }
        return "line " + line + " (`" + sourceLine + "`)";
    }

    private String sourceLine(String code, int line) {
        if (line <= 0) {
            return null;
        }

        String[] lines = code.split("\\R", -1);
        if (line > lines.length) {
            return null;
        }

        String trimmed = lines[line - 1].trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed.length() > 120 ? trimmed.substring(0, 117) + "..." : trimmed;
    }

    private String syntaxFixSuggestion(String compilerMessage) {
        String normalized = compilerMessage.toLowerCase(Locale.ENGLISH);

        if (normalized.contains("';' expected")) {
            return "Add the missing semicolon on or just before this line, then rerun the analysis.";
        }
        if (normalized.contains("')' expected")) {
            return "Close the missing parenthesis in this statement or method call, then try again.";
        }
        if (normalized.contains("'}' expected")) {
            return "Add the missing closing brace so the current block, class, or method is properly closed.";
        }
        if (normalized.contains("reached end of file while parsing")) {
            return "You likely have an unclosed block, class, or method. Add the missing closing brace near the end of the file.";
        }
        if (normalized.contains("illegal start of expression") || normalized.contains("illegal start of type")) {
            return "Check for a missing brace, stray keyword, or statement placed in the wrong block before this line.";
        }

        return "Fix the compiler-reported syntax issue on this line first, then run the analysis again.";
    }

    private boolean isSyntaxCompilerMessage(String normalizedCompilerMessage) {
        return normalizedCompilerMessage.contains("';' expected")
                || normalizedCompilerMessage.contains("')' expected")
                || normalizedCompilerMessage.contains("'}' expected")
                || normalizedCompilerMessage.contains("']' expected")
                || normalizedCompilerMessage.contains("reached end of file while parsing")
                || normalizedCompilerMessage.contains("illegal start of expression")
                || normalizedCompilerMessage.contains("illegal start of type")
                || normalizedCompilerMessage.contains("not a statement");
    }

    private String normalizeCompilerMessage(String compilerMessage) {
        return compilerMessage.replaceAll("\\s+", " ").trim();
    }

    private String ensureSentence(String message) {
        if (message.endsWith(".") || message.endsWith("!") || message.endsWith("?")) {
            return message;
        }
        return message + ".";
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null) {
            return;
        }

        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private void analyzeBlock(BlockStmt block,
                              int lineOffset,
                              Map<String, NullState> nullStates,
                              Map<String, NumericState> numericStates,
                              List<Finding> findings) {
        for (Statement statement : block.getStatements()) {
            analyzeStatement(statement, lineOffset, nullStates, numericStates, findings);
        }
    }

    private void analyzeStatement(Statement statement,
                                  int lineOffset,
                                  Map<String, NullState> nullStates,
                                  Map<String, NumericState> numericStates,
                                  List<Finding> findings) {
        if (statement.isBlockStmt()) {
            analyzeBlock(statement.asBlockStmt(), lineOffset, new HashMap<>(nullStates), new HashMap<>(numericStates), findings);
            return;
        }

        if (statement.isIfStmt()) {
            IfStmt ifStmt = statement.asIfStmt();
            inspectNode(ifStmt.getCondition(), lineOffset, nullStates, numericStates, findings);
            analyzeBranch(ifStmt.getThenStmt(), lineOffset, new HashMap<>(nullStates), new HashMap<>(numericStates), findings);
            ifStmt.getElseStmt().ifPresent(elseStmt ->
                    analyzeBranch(elseStmt, lineOffset, new HashMap<>(nullStates), new HashMap<>(numericStates), findings));
            return;
        }

        if (statement.isWhileStmt()) {
            WhileStmt whileStmt = statement.asWhileStmt();
            inspectNode(whileStmt.getCondition(), lineOffset, nullStates, numericStates, findings);
            analyzeBranch(whileStmt.getBody(), lineOffset, new HashMap<>(nullStates), new HashMap<>(numericStates), findings);
            return;
        }

        if (statement.isDoStmt()) {
            DoStmt doStmt = statement.asDoStmt();
            analyzeBranch(doStmt.getBody(), lineOffset, new HashMap<>(nullStates), new HashMap<>(numericStates), findings);
            inspectNode(doStmt.getCondition(), lineOffset, nullStates, numericStates, findings);
            return;
        }

        if (statement.isForStmt()) {
            ForStmt forStmt = statement.asForStmt();
            forStmt.getInitialization().forEach(expression ->
                    analyzeExpression(expression, lineOffset, nullStates, numericStates, findings));
            forStmt.getCompare().ifPresent(compare ->
                    inspectNode(compare, lineOffset, nullStates, numericStates, findings));
            analyzeBranch(forStmt.getBody(), lineOffset, new HashMap<>(nullStates), new HashMap<>(numericStates), findings);
            forStmt.getUpdate().forEach(update ->
                    inspectNode(update, lineOffset, nullStates, numericStates, findings));
            return;
        }

        if (statement.isForEachStmt()) {
            ForEachStmt forEachStmt = statement.asForEachStmt();
            inspectNode(forEachStmt.getIterable(), lineOffset, nullStates, numericStates, findings);
            analyzeBranch(forEachStmt.getBody(), lineOffset, new HashMap<>(nullStates), new HashMap<>(numericStates), findings);
            return;
        }

        if (statement.isExpressionStmt()) {
            analyzeExpression(statement.asExpressionStmt().getExpression(), lineOffset, nullStates, numericStates, findings);
            return;
        }

        inspectNode(statement, lineOffset, nullStates, numericStates, findings);
    }

    private void analyzeExpression(Expression expression,
                                   int lineOffset,
                                   Map<String, NullState> nullStates,
                                   Map<String, NumericState> numericStates,
                                   List<Finding> findings) {
        inspectNode(expression, lineOffset, nullStates, numericStates, findings);
        updateTrackedStates(expression, nullStates, numericStates);
    }

    private void analyzeBranch(Statement statement,
                               int lineOffset,
                               Map<String, NullState> nullStates,
                               Map<String, NumericState> numericStates,
                               List<Finding> findings) {
        if (statement.isBlockStmt()) {
            analyzeBlock(statement.asBlockStmt(), lineOffset, nullStates, numericStates, findings);
        } else {
            analyzeStatement(statement, lineOffset, nullStates, numericStates, findings);
        }
    }

    private void inspectNode(Node node,
                             int lineOffset,
                             Map<String, NullState> nullStates,
                             Map<String, NumericState> numericStates,
                             List<Finding> findings) {
        inspectMethodCalls(node, lineOffset, nullStates, findings);
        inspectArithmeticExpressions(node, lineOffset, numericStates, findings);
    }

    private void inspectMethodCalls(Node node,
                                    int lineOffset,
                                    Map<String, NullState> nullStates,
                                    List<Finding> findings) {
        node.findAll(MethodCallExpr.class).forEach(call -> {
            if (call.getScope().isPresent() && call.getScope().get().isNameExpr()) {
                String variableName = call.getScope().get().asNameExpr().getNameAsString();
                if (nullStates.getOrDefault(variableName, NullState.UNKNOWN) == NullState.NULL) {
                    findings.add(new Finding(
                            Severity.ERROR,
                            lineOf(call, lineOffset),
                            "Possible null pointer dereference",
                            "This method call uses a variable that is currently assigned null, so it can crash with NullPointerException.",
                            "Check the variable for null before using it, or initialize it with a real object first.",
                            List.of("https://docs.oracle.com/javase/8/docs/api/java/lang/NullPointerException.html")
                    ));
                }
            }

            if (call.getScope().isPresent() && call.getScope().get().isNullLiteralExpr()) {
                findings.add(new Finding(
                        Severity.ERROR,
                        lineOf(call, lineOffset),
                        "Null pointer dereference",
                        "This method is being called directly on null.",
                        "Store a real object in the variable before calling methods on it, or add a null check first.",
                        List.of("https://docs.oracle.com/javase/8/docs/api/java/lang/NullPointerException.html")
                ));
            }
        });
    }

    private void inspectArithmeticExpressions(Node node,
                                              int lineOffset,
                                              Map<String, NumericState> numericStates,
                                              List<Finding> findings) {
        node.findAll(BinaryExpr.class).forEach(expression -> {
            if (!isIntegerDivisionOperation(expression)) {
                return;
            }

            if (isDefinitelyZero(expression.getRight(), numericStates)) {
                findings.add(new Finding(
                        Severity.ERROR,
                        lineOf(expression, lineOffset),
                        "Division by zero",
                        "This expression divides by a value that is definitely zero, which throws ArithmeticException at runtime.",
                        "Guard the denominator before dividing, or change the value so it cannot be zero here.",
                        List.of("https://docs.oracle.com/javase/8/docs/api/java/lang/ArithmeticException.html")
                ));
            }
        });
    }

    private void updateTrackedStates(Expression expression,
                                     Map<String, NullState> nullStates,
                                     Map<String, NumericState> numericStates) {
        if (expression.isVariableDeclarationExpr()) {
            expression.asVariableDeclarationExpr().getVariables().forEach(variable -> {
                Expression initializer = variable.getInitializer().orElse(null);
                nullStates.put(variable.getNameAsString(), classifyNullState(initializer, nullStates));
                numericStates.put(variable.getNameAsString(), classifyNumericState(initializer, numericStates));
            });
            return;
        }

        if (!expression.isAssignExpr()) {
            return;
        }

        AssignExpr assignExpr = expression.asAssignExpr();
        if (!assignExpr.getTarget().isNameExpr()) {
            return;
        }

        String variableName = assignExpr.getTarget().asNameExpr().getNameAsString();
        if (assignExpr.getOperator() == AssignExpr.Operator.ASSIGN) {
            nullStates.put(variableName, classifyNullState(assignExpr.getValue(), nullStates));
            numericStates.put(variableName, classifyNumericState(assignExpr.getValue(), numericStates));
            return;
        }

        nullStates.put(variableName, NullState.UNKNOWN);
        numericStates.put(variableName, NumericState.UNKNOWN);
    }

    private NullState classifyNullState(Expression expression, Map<String, NullState> nullStates) {
        Expression value = unwrap(expression);
        if (value == null || value instanceof NullLiteralExpr) {
            return NullState.NULL;
        }
        if (value instanceof StringLiteralExpr || value instanceof ObjectCreationExpr || value instanceof ThisExpr) {
            return NullState.NON_NULL;
        }
        if (value instanceof NameExpr nameExpr) {
            return nullStates.getOrDefault(nameExpr.getNameAsString(), NullState.UNKNOWN);
        }
        return NullState.UNKNOWN;
    }

    private NumericState classifyNumericState(Expression expression, Map<String, NumericState> numericStates) {
        Expression value = unwrap(expression);
        if (value == null) {
            return NumericState.UNKNOWN;
        }
        if (value instanceof NameExpr nameExpr) {
            return numericStates.getOrDefault(nameExpr.getNameAsString(), NumericState.UNKNOWN);
        }
        if (value.isIntegerLiteralExpr()) {
            return value.asIntegerLiteralExpr().asNumber().intValue() == 0 ? NumericState.ZERO : NumericState.NON_ZERO;
        }
        if (value.isLongLiteralExpr()) {
            return value.asLongLiteralExpr().asNumber().longValue() == 0L ? NumericState.ZERO : NumericState.NON_ZERO;
        }
        if (value instanceof UnaryExpr unaryExpr
                && (unaryExpr.getOperator() == UnaryExpr.Operator.PLUS
                || unaryExpr.getOperator() == UnaryExpr.Operator.MINUS)) {
            return classifyNumericState(unaryExpr.getExpression(), numericStates);
        }
        return NumericState.UNKNOWN;
    }

    private boolean isStringReferenceComparison(BinaryExpr expression, Set<String> stringVariables) {
        if (isNullComparison(expression)) {
            return false;
        }
        return (expression.getOperator() == BinaryExpr.Operator.EQUALS
                || expression.getOperator() == BinaryExpr.Operator.NOT_EQUALS)
                && (isStringLikeExpression(expression.getLeft(), stringVariables)
                || isStringLikeExpression(expression.getRight(), stringVariables));
    }

    private boolean isNullComparison(BinaryExpr expression) {
        Expression left = unwrap(expression.getLeft());
        Expression right = unwrap(expression.getRight());
        return (left != null && left.isNullLiteralExpr()) || (right != null && right.isNullLiteralExpr());
    }

    private boolean isStringLikeExpression(Expression expression, Set<String> stringVariables) {
        Expression value = unwrap(expression);
        if (value instanceof StringLiteralExpr) {
            return true;
        }
        if (value instanceof NameExpr nameExpr) {
            return stringVariables.contains(nameExpr.getNameAsString());
        }
        return false;
    }

    private void addAssignmentConditionFinding(Expression condition,
                                               int lineOffset,
                                               String statementType,
                                               List<Finding> findings) {
        Expression value = unwrap(condition);
        if (!value.isAssignExpr()) {
            return;
        }

        findings.add(new Finding(
                Severity.WARNING,
                lineOf(value, lineOffset),
                "Assignment in " + statementType + " condition",
                "This " + statementType + " condition assigns a value instead of checking one, which is usually a bug.",
                "Replace the assignment with a comparison, or compute the value before the " + statementType + " statement.",
                List.of("https://docs.oracle.com/javase/tutorial/java/nutsandbolts/if.html")
        ));
    }

    private void addEmptyControlFinding(Statement statement,
                                        int lineOffset,
                                        String statementType,
                                        List<Finding> findings) {
        if (!statement.isEmptyStmt()) {
            return;
        }

        findings.add(new Finding(
                Severity.WARNING,
                lineOf(statement, lineOffset),
                "Empty " + statementType + " body",
                "This " + statementType + " statement ends immediately, which often means a stray semicolon or an accidentally empty block.",
                "Remove the stray semicolon or add the statement block that should run inside the " + statementType + ".",
                List.of("https://docs.oracle.com/javase/tutorial/java/nutsandbolts/if.html")
        ));
    }

    private void addConstantConditionFinding(Expression condition,
                                             int lineOffset,
                                             String statementType,
                                             List<Finding> findings) {
        Expression value = unwrap(condition);
        if (!(value instanceof BooleanLiteralExpr literalExpr)) {
            return;
        }

        findings.add(new Finding(
                Severity.WARNING,
                lineOf(value, lineOffset),
                "Constant boolean condition",
                "This " + statementType + " condition is always " + literalExpr.getValue() + ", so one branch can never behave differently.",
                "Replace the hard-coded boolean with a real condition or remove the dead branch.",
                List.of("https://docs.oracle.com/javase/tutorial/java/nutsandbolts/if.html")
        ));
    }

    private boolean isRawCollectionType(com.github.javaparser.ast.type.Type type) {
        if (!(type instanceof ClassOrInterfaceType classType)) {
            return false;
        }
        return RAW_COLLECTION_TYPES.contains(classType.getNameAsString()) && classType.getTypeArguments().isEmpty();
    }

    private boolean isBroadCatchType(String caughtType) {
        return "Exception".equals(caughtType)
                || "Throwable".equals(caughtType)
                || "RuntimeException".equals(caughtType);
    }

    private boolean isLocalVariable(VariableDeclarator variable) {
        return variable.findAncestor(FieldDeclaration.class).isEmpty();
    }

    private boolean isIntegerDivisionOperation(BinaryExpr expression) {
        if (expression.getOperator() != BinaryExpr.Operator.DIVIDE
                && expression.getOperator() != BinaryExpr.Operator.REMAINDER) {
            return false;
        }

        return !looksFloatingPoint(expression.getLeft()) && !looksFloatingPoint(expression.getRight());
    }

    private boolean looksFloatingPoint(Expression expression) {
        Expression value = unwrap(expression);
        return value != null && value.isDoubleLiteralExpr();
    }

    private boolean isDefinitelyZero(Expression expression, Map<String, NumericState> numericStates) {
        Expression value = unwrap(expression);
        if (value == null) {
            return false;
        }
        if (value.isIntegerLiteralExpr()) {
            return value.asIntegerLiteralExpr().asNumber().intValue() == 0;
        }
        if (value.isLongLiteralExpr()) {
            return value.asLongLiteralExpr().asNumber().longValue() == 0L;
        }
        if (value instanceof NameExpr nameExpr) {
            return numericStates.getOrDefault(nameExpr.getNameAsString(), NumericState.UNKNOWN) == NumericState.ZERO;
        }
        if (value instanceof UnaryExpr unaryExpr
                && (unaryExpr.getOperator() == UnaryExpr.Operator.PLUS
                || unaryExpr.getOperator() == UnaryExpr.Operator.MINUS)) {
            return isDefinitelyZero(unaryExpr.getExpression(), numericStates);
        }
        return false;
    }

    private Expression unwrap(Expression expression) {
        if (expression == null) {
            return null;
        }

        Expression value = expression;
        while (value.isEnclosedExpr()) {
            value = value.asEnclosedExpr().getInner();
        }
        return value;
    }

    private int severityWeight(Finding finding) {
        return switch (finding.severity()) {
            case ERROR -> 0;
            case WARNING -> 1;
            case TIP -> 2;
        };
    }

    private int lineOf(Node node, int lineOffset) {
        return Math.max(1, node.getBegin().map(position -> position.line - lineOffset).orElse(1));
    }

    private int extractSyntaxErrorLine(ParseProblemException exception, int lineOffset) {
        for (var problem : exception.getProblems()) {
            Matcher matcher = LINE_PATTERN.matcher(problem.getVerboseMessage());
            if (matcher.find()) {
                return Math.max(1, Integer.parseInt(matcher.group(1)) - lineOffset);
            }
        }
        return 1;
    }

    private record ParsedCode(CompilationUnit compilationUnit, int lineOffset) {
    }

    private record HeaderSplit(String header, String body) {
    }

    private record SourceVariant(String source, int lineOffset, String fileName) {
    }

    private record CompilerDiagnosticDetail(int line, String message) {
    }

    private record CannotFindSymbolDetail(String kind, String description, String location) {
        private String displayName() {
            String value = description.trim();
            int methodStart = value.indexOf('(');
            if (methodStart > 0) {
                int lastSpace = value.lastIndexOf(' ', methodStart);
                return value.substring(lastSpace + 1, methodStart);
            }

            int lastDot = value.lastIndexOf('.');
            if (lastDot >= 0 && lastDot < value.length() - 1) {
                value = value.substring(lastDot + 1);
            }

            int lastSpace = value.lastIndexOf(' ');
            return lastSpace >= 0 ? value.substring(lastSpace + 1) : value;
        }
    }

    private enum NullState {
        NULL,
        NON_NULL,
        UNKNOWN
    }

    private enum NumericState {
        ZERO,
        NON_ZERO,
        UNKNOWN
    }

    private enum AnalysisLevel {
        BEGINNER("beginner", "Beginner", 6),
        INTERMEDIATE("intermediate", "Intermediate", 9),
        ADVANCED("advanced", "Advanced", 12);

        private final String value;
        private final String displayName;
        private final int checksApplied;

        AnalysisLevel(String value, String displayName, int checksApplied) {
            this.value = value;
            this.displayName = displayName;
            this.checksApplied = checksApplied;
        }

        private static AnalysisLevel from(String value) {
            if (value == null || value.isBlank()) {
                return BEGINNER;
            }
            for (AnalysisLevel level : values()) {
                if (level.value.equalsIgnoreCase(value.trim())) {
                    return level;
                }
            }
            return BEGINNER;
        }

        private boolean includes(AnalysisLevel other) {
            return ordinal() >= other.ordinal();
        }

        private String value() {
            return value;
        }

        private String displayName() {
            return displayName;
        }

        private int checksApplied() {
            return checksApplied;
        }
    }

    private static final class SnippetParseException extends RuntimeException {
        private final ParseProblemException exception;
        private final int lineOffset;

        private SnippetParseException(ParseProblemException exception, int lineOffset) {
            super(exception);
            this.exception = exception;
            this.lineOffset = lineOffset;
        }

        private ParseProblemException exception() {
            return exception;
        }

        private int lineOffset() {
            return lineOffset;
        }
    }

    private static final class InMemoryJavaSource extends SimpleJavaFileObject {
        private final String source;

        private InMemoryJavaSource(String fileName, String source) {
            super(URI.create("string:///" + fileName), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
