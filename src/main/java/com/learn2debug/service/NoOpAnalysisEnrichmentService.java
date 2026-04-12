package com.learn2debug.service;

import com.learn2debug.model.AnalysisResponse;
import org.springframework.stereotype.Service;

@Service
public class NoOpAnalysisEnrichmentService implements AnalysisEnrichmentService {

    @Override
    public AnalysisResponse enrich(String code, AnalysisResponse response) {
        return response;
    }
}
