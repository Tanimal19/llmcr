package com.llmcr.feature.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.llmcr.BaseIntegrationTest;
import com.llmcr.domain.exception.APIServiceException;
import com.llmcr.domain.exception.APIServiceException.ErrorCode;
import com.llmcr.feature.sync.SourceSyncService.TrackRootPreview;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class SourceSyncServiceIT extends BaseIntegrationTest {

    @Autowired
    SourceSyncService sourceSyncService;

    @MockitoBean
    SourcePreviewService sourcePreviewService;

    private static final Logger logger = LoggerFactory.getLogger(SourceSyncServiceIT.class);

    @BeforeEach
    private void setup(TestInfo testInfo) {
        logger.info("Ready to test: {}", testInfo.getDisplayName());
    }

    @Test
    @DisplayName("S4-1-1: Successful getAllTrackRootPreview with multiple track roots")
    void testS4_1_1() {
        TrackRootPreview preview1 = new TrackRootPreview(1L, "/nonexistent/path/for/test/1", true, null, List.of());
        TrackRootPreview preview2 = new TrackRootPreview(2L, "/nonexistent/path/for/test/2", true, null, List.of());
        when(sourcePreviewService.getAllTrackRootPreview()).thenReturn(List.of(preview1, preview2));

        List<TrackRootPreview> results = sourceSyncService.getAllTrackRootPreview();

        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(preview -> {
            assertThat(preview).isNotNull();
            assertThat(preview.isSynced()).isTrue();
            assertThat(preview.sources()).isEmpty();
        });
        verify(sourcePreviewService).getAllTrackRootPreview();
    }

    @Test
    @DisplayName("S4-1-2: Successful getAllTrackRootPreview with no track roots")
    void testS4_1_2() {
        when(sourcePreviewService.getAllTrackRootPreview()).thenReturn(List.of());

        List<TrackRootPreview> results = sourceSyncService.getAllTrackRootPreview();

        assertThat(results).isEmpty();
        verify(sourcePreviewService).getAllTrackRootPreview();
    }

    @Test
    @DisplayName("S4-3-1: Database access fail when calling getAllTrackRootPreview")
    void testS4_3_1() {
        when(sourcePreviewService.getAllTrackRootPreview()).thenThrow(new APIServiceException(
                ErrorCode.SOURCE_SYNC_PREVIEW_LIST_FAILED,
                "Failed to list track root previews"));

        assertThatThrownBy(() -> sourceSyncService.getAllTrackRootPreview())
                .isInstanceOf(APIServiceException.class)
                .extracting(e -> ((APIServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.SOURCE_SYNC_PREVIEW_LIST_FAILED);
    }
}
