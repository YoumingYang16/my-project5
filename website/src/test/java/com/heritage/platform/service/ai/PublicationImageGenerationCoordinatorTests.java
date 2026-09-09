package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.AiCoverCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicationImageGenerationCoordinatorTests {

    @Test
    void fallsBackToLegacyComfyUiWhenRendererFails() {
        RendererApiImageProvider renderer = mock(RendererApiImageProvider.class);
        LegacyComfyUiImageProvider legacy = mock(LegacyComfyUiImageProvider.class);
        when(renderer.enabled()).thenReturn(true);
        when(renderer.generate(any())).thenThrow(new AiCoverWorkflowException("offline"));
        when(legacy.enabled()).thenReturn(true);
        List<AiCoverCandidate> expected = List.of(new AiCoverCandidate(
                "candidate-1", "/uploads/generated-covers/1/candidate.jpg", 1L, false, "prompt"
        ));
        when(legacy.generate(any())).thenReturn(expected);
        PublicationImageGenerationCoordinator coordinator = new PublicationImageGenerationCoordinator(renderer, legacy);
        ReflectionTestUtils.setField(coordinator, "preferredProvider", "renderer-api");
        ReflectionTestUtils.setField(coordinator, "legacyFallbackEnabled", true);

        List<AiCoverCandidate> actual = coordinator.generate(new PublicationImageGenerationRequest(
                1L, "title", "abstract", null, null, null, null, null, null, "prompt", "negative"
        ));

        assertThat(actual).isEqualTo(expected);
        verify(renderer).generate(any());
        verify(legacy).generate(any());
    }
    @Test
    void doesNotFallBackToComfyUiWhenDoubaoGenerationFails() {
        RendererApiImageProvider renderer = mock(RendererApiImageProvider.class);
        LegacyComfyUiImageProvider legacy = mock(LegacyComfyUiImageProvider.class);
        when(renderer.enabled()).thenReturn(true);
        when(renderer.generate(any())).thenThrow(new AiCoverWorkflowException(
                "renderer-api returned HTTP 502: DOUBAO_IMAGE_GENERATION_FAILED"
        ));
        when(legacy.enabled()).thenReturn(true);
        PublicationImageGenerationCoordinator coordinator = new PublicationImageGenerationCoordinator(renderer, legacy);
        ReflectionTestUtils.setField(coordinator, "preferredProvider", "renderer-api");
        ReflectionTestUtils.setField(coordinator, "legacyFallbackEnabled", true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> coordinator.generate(new PublicationImageGenerationRequest(
                1L, "title", "abstract", null, null, null, null, null, null, "prompt", "negative"
        ))).isInstanceOf(AiCoverWorkflowException.class)
                .hasMessageContaining("DOUBAO_IMAGE_GENERATION_FAILED");
        verify(renderer).generate(any());
        verify(legacy, org.mockito.Mockito.never()).generate(any());
    }
}
