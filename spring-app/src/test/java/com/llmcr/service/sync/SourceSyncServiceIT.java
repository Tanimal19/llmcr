package com.llmcr.service.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.llmcr.BaseIntegrationTest;
import com.llmcr.api.APIServiceException;
import com.llmcr.api.APIServiceException.ErrorCode;
import com.llmcr.entity.Source;
import com.llmcr.entity.TrackRoot;
import com.llmcr.repository.TrackRootRepository;
import com.llmcr.service.sync.SourceSyncService.TrackRootPreview;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class SourceSyncServiceIT extends BaseIntegrationTest {

    @Autowired
    SourceSyncService sourceSyncService;

    @MockitoBean
    TrackRootRepository trackRootRepository;

    private static final Logger logger = LoggerFactory.getLogger(SourceSyncServiceIT.class);

    @BeforeEach
    private void setup(TestInfo testInfo) {
        logger.info("Ready to test: {}", testInfo.getDisplayName());
        ReflectionTestUtils.setField(sourceSyncService, "trackRootRepository", trackRootRepository);
    }

    @Test
    @DisplayName("S4-1-1: Successful getAllTrackRootPreview with multiple track roots")
    void testS4_1_1() {
        TrackRoot trackRoot1 = new TrackRoot("/nonexistent/path/for/test/1", Set.of(Source.SourceType.PDF));
        TrackRoot trackRoot2 = new TrackRoot("/nonexistent/path/for/test/2", Set.of(Source.SourceType.PDF));
        when(trackRootRepository.findAllIds()).thenReturn(List.of(1L, 2L));
        when(trackRootRepository.findById(1L)).thenReturn(Optional.of(trackRoot1));
        when(trackRootRepository.findById(2L)).thenReturn(Optional.of(trackRoot2));

        List<TrackRootPreview> results = sourceSyncService.getAllTrackRootPreview();

        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(preview -> {
            assertThat(preview).isNotNull();
            assertThat(preview.isSynced()).isTrue();
            assertThat(preview.sources()).isEmpty();
        });
        verify(trackRootRepository).findAllIds();
    }

    @Test
    @DisplayName("S4-1-2: Successful getAllTrackRootPreview with no track roots")
    void testS4_1_2() {
        when(trackRootRepository.findAllIds()).thenReturn(List.of());

        List<TrackRootPreview> results = sourceSyncService.getAllTrackRootPreview();

        assertThat(results).isEmpty();
        verify(trackRootRepository).findAllIds();
    }

    @Test
    @DisplayName("S4-3-1: Database access fail when calling getAllTrackRootPreview")
    void testS4_3_1() {
        when(trackRootRepository.findAllIds()).thenThrow(new RuntimeException("DB connection failed"));

        assertThatThrownBy(() -> sourceSyncService.getAllTrackRootPreview())
            .isInstanceOf(APIServiceException.class)
            .extracting(e -> ((APIServiceException) e).getErrorCode())
            .isEqualTo(ErrorCode.SOURCE_SYNC_PREVIEW_LIST_FAILED);
    }
}
