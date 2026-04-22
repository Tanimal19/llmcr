package com.llmcr.service.etl.operation;

public interface ETLOperation<I, O> {
    O execute(I input);
}
