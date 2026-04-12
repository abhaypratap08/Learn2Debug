package com.learn2debug.service;

import com.learn2debug.model.AnalysisResponse;
import com.learn2debug.model.Severity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeAnalysisServiceTest {

    @Test
    void analyzeWarnsWhenCompilerIsUnavailable() {
        CodeAnalysisService service = new CodeAnalysisService(() -> null);

        AnalysisResponse response = service.analyze("int count = \"wrong\";", "beginner");

        assertEquals(1, response.findings().size());
        assertEquals("Compiler unavailable", response.findings().getFirst().title());
        assertEquals(Severity.WARNING, response.findings().getFirst().severity());
        assertTrue(response.findings().getFirst().explanation().contains("without javac"));
        assertNotEquals("No obvious issues found", response.findings().getFirst().title());
    }
}
