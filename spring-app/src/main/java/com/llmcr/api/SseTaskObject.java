package com.llmcr.api;

import com.llmcr.api.SseTaskManager.TaskProgressEvent;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface SseTaskObject<I, O> {
    public String getTaskName();

    public O execute(I input, Consumer<TaskProgressEvent> progressListener, BooleanSupplier cancellationRequested);
}
