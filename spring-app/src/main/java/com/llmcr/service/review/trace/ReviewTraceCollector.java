package com.llmcr.service.review.trace;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ReviewTraceCollector {

    public String traceId = UUID.randomUUID().toString();
    public long durationMillis;
    public String error;
    public Map<String, Object> metadata = new LinkedHashMap<>();
    public Map<String, Integer> clientCallCounts = new LinkedHashMap<>();
    public List<AgentCallEntry> agentCalls = new ArrayList<>();

    @JsonIgnore
    private final long startedAtEpochMillis = Instant.now().toEpochMilli();

    public void putMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    public void addAgentCall(AgentCallEntry entry) {
        agentCalls.add(entry);
        if (entry.clientType != null) {
            clientCallCounts.merge(entry.clientType, 1, (a, b) -> a + b);
        }
    }

    public void complete(Throwable error) {
        this.durationMillis = Instant.now().toEpochMilli() - startedAtEpochMillis;
        if (error != null) {
            this.error = stackTrace(error);
        }
    }

    private String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
