package com.llmcr.api.sse;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public abstract class VoidSseTaskObject extends SseTaskObject<Void, Void> {

    public Void execute(Void input, Consumer<SseTaskProgress> progressListener,
            BooleanSupplier cancellationRequested) {
        execute(progressListener, cancellationRequested);
        return null;
    }

    public abstract void execute(Consumer<SseTaskProgress> progressListener,
            BooleanSupplier cancellationRequested);
}
