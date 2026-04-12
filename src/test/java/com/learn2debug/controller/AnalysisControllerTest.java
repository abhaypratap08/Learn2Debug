package com.learn2debug.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn2debug.model.AnalysisRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthEndpointReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void analyzeReturnsFindingsAndDocumentationLinks() throws Exception {
        AnalysisRequest request = new AnalysisRequest("int x = 4 / 0;\nString temp = \"demo\";", "beginner");

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").exists())
                .andExpect(jsonPath("$.level").value("beginner"))
                .andExpect(jsonPath("$.checksApplied").value(6))
                .andExpect(jsonPath("$.findings[0].severity").value("ERROR"))
                .andExpect(jsonPath("$.findings[0].relatedDocumentation[0]").exists());
    }

    @Test
    void analyzeValidatesRequiredCode() throws Exception {
        AnalysisRequest request = new AnalysisRequest("", "beginner");

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("code is required"));
    }

    @Test
    void analyzeFlagsStringComparisonAndNullDereferenceExamples() throws Exception {
        AnalysisRequest request = new AnalysisRequest(
                "String s = null;\n" +
                "String a = \"hello\";\n" +
                "if (a == \"hello\") {\n" +
                "    System.out.println(s.length());\n" +
                "}\n",
                "beginner"
        );

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[0].title").isNotEmpty())
                .andExpect(jsonPath("$.findings[?(@.title=='String comparison with ==')]").exists())
                .andExpect(jsonPath("$.findings[?(@.title=='Possible null pointer dereference')]").exists());
    }

    @Test
    void analyzeReportsSnippetSyntaxErrorsUsingUserLineNumbers() throws Exception {
        AnalysisRequest request = new AnalysisRequest(
                "int x = 1\nSystem.out.println(x);",
                "beginner"
        );

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[0].title").value("Syntax error"))
                .andExpect(jsonPath("$.findings[0].line").value(1))
                .andExpect(jsonPath("$.findings[0].explanation", containsString("Java compiler reported")))
                .andExpect(jsonPath("$.findings[0].fixSuggestion", containsString("semicolon")));
    }

    @Test
    void analyzeReportsDeepSnippetSyntaxErrorsUsingCompilerLineNumbers() throws Exception {
        StringBuilder code = new StringBuilder();
        for (int line = 1; line <= 25; line++) {
            code.append("int line").append(line).append(" = ").append(line).append(";\n");
        }
        code.append("System.out.println(\"missing semicolon\")");

        AnalysisRequest request = new AnalysisRequest(code.toString(), "beginner");

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[0].title").value("Syntax error"))
                .andExpect(jsonPath("$.findings[0].line").value(26))
                .andExpect(jsonPath("$.findings[0].explanation", containsString("Java compiler reported")));
    }

    @Test
    void analyzeDoesNotFlagNullDereferenceAfterVariableIsReassigned() throws Exception {
        AnalysisRequest request = new AnalysisRequest(
                "String s = null;\n" +
                "s = \"ok\";\n" +
                "System.out.println(s.length());",
                "beginner"
        );

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[0].title").value("No obvious issues found"));
    }

    @Test
    void analyzeFlagsNullDereferenceAfterAssignmentToNull() throws Exception {
        AnalysisRequest request = new AnalysisRequest(
                "String value = \"safe\";\n" +
                "value = null;\n" +
                "System.out.println(value.length());",
                "beginner"
        );

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[?(@.title=='Possible null pointer dereference')]").exists());
    }

    @Test
    void analyzeFlagsStringComparisonForStringParameters() throws Exception {
        AnalysisRequest request = new AnalysisRequest(
                "class Demo {\n" +
                "    void compare(String left, String right) {\n" +
                "        if (left == right) {\n" +
                "            System.out.println(left);\n" +
                "        }\n" +
                "    }\n" +
                "}",
                "beginner"
        );

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[?(@.title=='String comparison with ==')]").exists());
    }

    @Test
    void analyzeFlagsStringInequalityReferenceChecks() throws Exception {
        AnalysisRequest request = new AnalysisRequest(
                "String left = \"hello\";\n" +
                "if (left != \"bye\") {\n" +
                "    System.out.println(left);\n" +
                "}",
                "beginner"
        );

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[?(@.title=='String comparison with !=')]").exists());
    }

    @Test
    void analyzeDoesNotFlagStringNullChecksAsReferenceComparison() throws Exception {
        AnalysisRequest request = new AnalysisRequest(
                "String value = readValue();\n" +
                "if (value != null) {\n" +
                "    System.out.println(value.trim());\n" +
                "}",
                "beginner"
        );

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[?(@.title=='String comparison with !=')]").doesNotExist());
    }

    @Test
    void analyzeFlagsDivisionByZeroThroughTrackedVariableState() throws Exception {
        AnalysisRequest request = new AnalysisRequest(
                "int denominator = 0;\n" +
                "int answer = 10 / denominator;",
                "beginner"
        );

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[?(@.title=='Division by zero')]").exists());
    }

    @Test
    void analyzeReportsCompilerErrorsForTypeMismatch() throws Exception {
        AnalysisRequest request = new AnalysisRequest(
                "int count = \"wrong\";",
                "beginner"
        );

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[0].title").value("Incompatible types"))
                .andExpect(jsonPath("$.findings[0].explanation", containsString("int count = \"wrong\";")))
                .andExpect(jsonPath("$.findings[0].explanation", containsString("where int is required")))
                .andExpect(jsonPath("$.findings[0].line").value(1))
                .andExpect(jsonPath("$.findings[0].fixSuggestion", containsString("produces int")));
    }

    @Test
    void analyzeReportsCompilerErrorsForUnknownSymbols() throws Exception {
        AnalysisRequest request = new AnalysisRequest(
                "System.out.println(totalScore);",
                "beginner"
        );

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[?(@.title=='Cannot find symbol')]").exists())
                .andExpect(jsonPath("$.findings[0].title").isNotEmpty())
                .andExpect(jsonPath("$.findings[0].explanation", containsString("totalScore")))
                .andExpect(jsonPath("$.findings[0].fixSuggestion", containsString("Declare `totalScore`")));
    }

    @Test
    void analyzeTreatsMissingArraysImportAsTypeErrorWhenFileAlreadyHasImports() throws Exception {
        AnalysisRequest request = new AnalysisRequest(
                "import java.util.List;\n" +
                "class Demo {\n" +
                "    void fill(int[] count) {\n" +
                "        Arrays.fill(count, 1);\n" +
                "    }\n" +
                "}",
                "beginner"
        );

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[0].title").value("Cannot find symbol"))
                .andExpect(jsonPath("$.findings[0].explanation", containsString("type `Arrays` is not known here")))
                .andExpect(jsonPath("$.findings[0].fixSuggestion", containsString("import java.util.Arrays;")));
    }

    @Test
    void analyzeReportsCompilerErrorsForIncompleteTreeSolution() throws Exception {
        AnalysisRequest request = new AnalysisRequest(
                "class Solution {\n" +
                "    public int[] sumOfDistancesInTree(int n, int[][] edges) {\n" +
                "        final ArrayList<Integer>[] graph = new ArrayList[n];\n" +
                "        final int[] count = new int[n];\n" +
                "        Arrays.fill(count, 1);\n" +
                "        final int[] answer = new int[n];\n" +
                "        for (int i = 0; i < graph.length; i++) {\n" +
                "            graph[i] = new ArrayList<>();\n" +
                "        }\n" +
                "        for (int[] edge : edges) {\n" +
                "            graph[edge[0]].add(edge[1]);\n" +
                "            graph[edge[1]].add(edge[0]);\n" +
                "        }\n" +
                "\n" +
                "        postOrder(0, -1, graph, count, answer);\n" +
                "        preOrder(0, -1, graph, count, answer, n);\n" +
                "\n" +
                "        return answer;\n" +
                "    }\n" +
                "\n" +
                "    private void preOrder(int node, int parent, ArrayList<Integer>[] graph, int[] count, int[] answer, int n) {\n" +
                "        for (int child : graph[node]) {\n" +
                "            if (child != parent) {\n" +
                "                answer[child] = answer[node] + (n - count[child]) - count[child];\n" +
                "                preOrder(child, node, graph, count, answer, n);\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}",
                "beginner"
        );

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[?(@.title=='Cannot find symbol')]").exists())
                .andExpect(jsonPath("$.findings[?(@.title=='No obvious issues found')]").doesNotExist())
                .andExpect(jsonPath("$.findings[0].line").value(15))
                .andExpect(jsonPath("$.findings[0].explanation", containsString("postOrder")))
                .andExpect(jsonPath("$.findings[0].explanation", containsString("line 15")))
                .andExpect(jsonPath("$.findings[0].fixSuggestion", containsString("Declare `postOrder")));
    }

    @Test
    void analyzeAllowsMainClassWithoutImportsForCommonJdkTypes() throws Exception {
        AnalysisRequest request = new AnalysisRequest(
                "class Main {\n" +
                "    public static void main(String[] args) throws Exception {\n" +
                "        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));\n" +
                "        String line = reader.readLine();\n" +
                "        if (line != null) {\n" +
                "            System.out.println(line.trim());\n" +
                "        }\n" +
                "    }\n" +
                "}",
                "beginner"
        );

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[0].title").value("No obvious issues found"))
                .andExpect(jsonPath("$.findings[?(@.title=='Cannot find symbol')]").doesNotExist());
    }

    @Test
    void analyzeAllowsJudgeStyleSolutionWithoutJavaUtilImports() throws Exception {
        AnalysisRequest request = new AnalysisRequest(
                "class Solution {\n" +
                "    public int[] sumOfDistancesInTree(int n, int[][] edges) {\n" +
                "        final ArrayList<Integer>[] graph = new ArrayList[n];\n" +
                "        final int[] count = new int[n];\n" +
                "        Arrays.fill(count, 1);\n" +
                "        final int[] answer = new int[n];\n" +
                "        for (int i = 0; i < graph.length; i++) {\n" +
                "            graph[i] = new ArrayList<>();\n" +
                "        }\n" +
                "        for (int[] edge : edges) {\n" +
                "            graph[edge[0]].add(edge[1]);\n" +
                "            graph[edge[1]].add(edge[0]);\n" +
                "        }\n" +
                "\n" +
                "        postOrder(0, -1, graph, count, answer);\n" +
                "        preOrder(0, -1, graph, count, answer, n);\n" +
                "\n" +
                "        return answer;\n" +
                "    }\n" +
                "\n" +
                "    private void postOrder(int node, int parent, ArrayList<Integer>[] graph, int[] count, int[] answer) {\n" +
                "        for (int child : graph[node]) {\n" +
                "            if (child != parent) {\n" +
                "                postOrder(child, node, graph, count, answer);\n" +
                "                count[node] += count[child];\n" +
                "                answer[node] += answer[child] + count[child];\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    private void preOrder(int node, int parent, ArrayList<Integer>[] graph, int[] count, int[] answer, int n) {\n" +
                "        for (int child : graph[node]) {\n" +
                "            if (child != parent) {\n" +
                "                answer[child] = answer[node] + (n - count[child]) - count[child];\n" +
                "                preOrder(child, node, graph, count, answer, n);\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}",
                "beginner"
        );

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[0].title").value("No obvious issues found"))
                .andExpect(jsonPath("$.findings[?(@.title=='Cannot find symbol')]").doesNotExist());
    }

    @Test
    void intermediateLevelAddsRawCollectionChecks() throws Exception {
        AnalysisRequest request = new AnalysisRequest(
                "java.util.List names = new java.util.ArrayList();",
                "intermediate"
        );

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("intermediate"))
                .andExpect(jsonPath("$.checksApplied").value(9))
                .andExpect(jsonPath("$.findings[?(@.title=='Raw collection type')]").exists());
    }

    @Test
    void beginnerLevelDoesNotRunIntermediateOnlyChecks() throws Exception {
        AnalysisRequest request = new AnalysisRequest(
                "java.util.List names = new java.util.ArrayList();",
                "beginner"
        );

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[0].title").value("No obvious issues found"));
    }

    @Test
    void advancedLevelAddsCatchQualityChecks() throws Exception {
        AnalysisRequest request = new AnalysisRequest(
                "try {\n" +
                "    runTask();\n" +
                "} catch (Exception ex) {\n" +
                "}\n",
                "advanced"
        );

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("advanced"))
                .andExpect(jsonPath("$.checksApplied").value(12))
                .andExpect(jsonPath("$.findings[?(@.title=='Overly broad catch clause')]").exists())
                .andExpect(jsonPath("$.findings[?(@.title=='Empty catch block')]").exists());
    }

    @Test
    void invalidLevelFallsBackToBeginner() throws Exception {
        AnalysisRequest request = new AnalysisRequest("int x = 4 / 0;", "expert");

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("beginner"))
                .andExpect(jsonPath("$.checksApplied").value(6));
    }

    @Test
    void preflightAllowsVercelPreviewOrigins() throws Exception {
        mockMvc.perform(options("/api/analyze")
                        .header("Origin", "https://learn2debug-git-fix-preview-abhay.vercel.app")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin",
                        "https://learn2debug-git-fix-preview-abhay.vercel.app"));
    }

    @Test
    void preflightAllowsLoopbackFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/analyze")
                        .header("Origin", "http://127.0.0.1:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:3000"));
    }

    @Test
    void preflightAllowsLanFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/analyze")
                        .header("Origin", "http://192.168.1.25:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://192.168.1.25:3000"));
    }
}
