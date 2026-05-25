package com.llmcr.api;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.llmcr.api.SseTaskManager.TaskProgressEvent;

public interface SseTaskObject<I, O> {

    public String getTaskName();

    public O execute(I input,
            Consumer<TaskProgressEvent> progressListener,
            BooleanSupplier cancellationRequested);
}
