package com.heritage.platform.controller;

import com.heritage.platform.dto.ai.SocialCopyGenerationRequest;
import com.heritage.platform.dto.ai.SocialCopyGenerationResult;
import com.heritage.platform.service.ai.PublicationSocialCopyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicationSocialCopyControllerTests {

    private PublicationSocialCopyService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(PublicationSocialCopyService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicationSocialCopyController(service)).build();
    }

    @Test
    void generatesSocialCopyUsingExistingApiResponseStyle() throws Exception {
        SocialCopyGenerationResult result = new SocialCopyGenerationResult(
                12L,
                "zh",
                "engaging",
                "social-media-ready",
                "CACHED_OPENAI",
                "【论文分享】示例文案 #研究分享",
                Map.of("engaging", "【论文分享】示例文案 #研究分享"),
                List.of("研究分享"),
                List.of()
        );
        when(service.generate(eq(12L), eq(new SocialCopyGenerationRequest("zh", "engaging"))))
                .thenReturn(result);

        mockMvc.perform(post("/api/publications/12/social-copy/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"zh","tone":"engaging"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("DeepSeek-generated social copy is based on extracted paper content."))
                .andExpect(jsonPath("$.data.publicationId").value(12))
                .andExpect(jsonPath("$.data.language").value("zh"))
                .andExpect(jsonPath("$.data.source").value("CACHED_OPENAI"))
                .andExpect(jsonPath("$.data.copyText").isNotEmpty());

        verify(service).generate(12L, new SocialCopyGenerationRequest("zh", "engaging"));
    }
}
