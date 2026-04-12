package com.learn2debug.service;

import com.learn2debug.model.AnalysisResponse;

public interface AnalysisEnrichmentService {

    AnalysisResponse enrich(String code, AnalysisResponse response);
}
