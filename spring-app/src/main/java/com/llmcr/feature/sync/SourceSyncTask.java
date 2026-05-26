package com.llmcr.feature.sync;

import com.llmcr.domain.sse.VoidSseTaskObject;
import com.llmcr.feature.sync.etl.ETLService;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class SourceSyncTask extends VoidSseTaskObject {

    private final SourceSyncService sourceSyncService;
    private final ETLService etlService;

    public SourceSyncTask(SourceSyncService sourceSyncService, ETLService etlService) {
        this.sourceSyncService = sourceSyncService;
        this.etlService = etlService;
    }

    @Override
    public String getTaskName() {
        return "SourceSync";
    }

    @Override
    public void execute(Consumer<SseTaskProgress> progressListener, BooleanSupplier cancellationRequested) {
        sourceSyncService.syncAllTrackRootSource(progressListener, cancellationRequested);
        etlService.execute(null, cancellationRequested);
    }
}
