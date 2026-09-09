package com.heritage.platform.controller;

import com.heritage.platform.dto.ai.AiCoverCandidate;
import com.heritage.platform.dto.ai.AiCoverGenerationResult;
import com.heritage.platform.dto.ai.AiCoverSelectionResponse;
import com.heritage.platform.service.ai.PublicationAiCoverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiCoverGenerationControllerTests {

    private PublicationAiCoverService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(PublicationAiCoverService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AiCoverGenerationController(service)).build();
    }

    @Test
    void existingGenerateCandidatesAndSelectEndpointsRemainCompatible() throws Exception {
        AiCoverGenerationResult generation = AiCoverGenerationResult.failed(12L, "generation result", List.of());
        AiCoverCandidate candidate = new AiCoverCandidate(
                "candidate-1", "/uploads/generated-covers/12/candidate-1.png", 1L, true, "prompt"
        );
        AiCoverSelectionResponse selection = new AiCoverSelectionResponse(
                12L, candidate.imageUrl(), "Selected image has been saved as the publication cover."
        );
        when(service.generate(12L)).thenReturn(generation);
        when(service.getExistingCandidates(12L)).thenReturn(List.of(candidate));
        when(service.selectCandidate(12L, candidate.candidateId(), candidate.imageUrl())).thenReturn(selection);

        mockMvc.perform(post("/api/admin/publications/12/ai-cover/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicationId").value(12));
        mockMvc.perform(get("/api/admin/publications/12/ai-cover/candidates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].candidateId").value("candidate-1"));
        mockMvc.perform(post("/api/admin/publications/12/ai-cover/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "candidateId": "candidate-1",
                                  "imageUrl": "/uploads/generated-covers/12/candidate-1.png"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverImageUrl").value(candidate.imageUrl()));

        verify(service).generate(12L);
        verify(service).getExistingCandidates(12L);
        verify(service).selectCandidate(12L, candidate.candidateId(), candidate.imageUrl());
    }
}
