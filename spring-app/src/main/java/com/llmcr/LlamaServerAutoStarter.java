package com.llmcr;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LlamaServerAutoStarter implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(LlamaServerAutoStarter.class);

    @Value("${llmcr.llama.autostart.enabled:true}")
    private boolean autoStartEnabled;

    @Value("${llmcr.llama.command:llama-server}")
    private String llamaCommand;

    @Value("${llmcr.llama.models-dir:}")
    private String modelsDir;

    @Value("${llmcr.llama.ctx-size:16384}")
    private int ctxSize;

    @Value("${llmcr.llama.batch-size:512}")
    private int batchSize;

    @Value("${llmcr.llama.ubatch-size:128}")
    private int ubatchSize;

    @Value("${llmcr.llama.parallel:1}")
    private int parallel;

    @Value("${llmcr.llama.host:0.0.0.0}")
    private String llamaHost;

    @Value("${llmcr.llama.start-timeout-ms:15000}")
    private long startTimeoutMs;

    @Value("${llmcr.chat.small.provider:openai}")
    private String smallChatProvider;

    @Value("${llmcr.chat.small.openai.url:}")
    private String smallChatUrl;

    @Value("${llmcr.chat.small.model:}")
    private String smallChatModel;

    @Value("${llmcr.chat.large.provider:openai}")
    private String largeChatProvider;

    @Value("${llmcr.chat.large.openai.url:}")
    private String largeChatUrl;

    @Value("${llmcr.chat.large.model:}")
    private String largeChatModel;

    @Value("${llmcr.embedding.provider:openai}")
    private String embeddingProvider;

    @Value("${llmcr.embedding.openai.url:}")
    private String embeddingUrl;

    @Value("${llmcr.embedding.model:}")
    private String embeddingModel;

    @Value("${llmcr.reranking.provider:openai}")
    private String rerankingProvider;

    @Value("${llmcr.reranking.openai.url:}")
    private String rerankingUrl;

    @Value("${llmcr.reranking.model:}")
    private String rerankingModel;

    private final List<ManagedProcess> managedProcesses = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean running;

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }

        running = true;
        if (!autoStartEnabled) {
            logger.info("llama.cpp auto-start is disabled (llmcr.llama.autostart.enabled=false).");
            return;
        }

        startIfConfigured(new ServerSpec("small-chat", smallChatProvider, smallChatUrl, smallChatModel, false, false));
        startIfConfigured(new ServerSpec("large-chat", largeChatProvider, largeChatUrl, largeChatModel, false, false));
        startIfConfigured(new ServerSpec("embedding", embeddingProvider, embeddingUrl, embeddingModel, true, false));
        startIfConfigured(new ServerSpec("reranking", rerankingProvider, rerankingUrl, rerankingModel, false, true));
    }

    @Override
    public synchronized void stop() {
        for (ManagedProcess managedProcess : new ArrayList<>(managedProcesses)) {
            Process process = managedProcess.process();
            if (!process.isAlive()) {
                continue;
            }

            logger.info("Stopping llama-server [{}] on port {}.", managedProcess.name(), managedProcess.port());
            process.destroy();
            try {
                boolean exited = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                if (!exited) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }

        managedProcesses.clear();
        running = false;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MIN_VALUE;
    }

    private void startIfConfigured(ServerSpec spec) {
        if (!"openai".equalsIgnoreCase(spec.provider())) {
            return;
        }

        if (!isConfiguredValue(spec.url()) || !isConfiguredValue(spec.model())) {
            return;
        }

        URI uri;
        try {
            uri = URI.create(spec.url());
        } catch (Exception e) {
            logger.warn("Skipping llama-server [{}]: invalid URL '{}'", spec.name(), spec.url());
            return;
        }

        if (uri.getPort() <= 0) {
            logger.warn("Skipping llama-server [{}]: URL must include a port: {}", spec.name(), spec.url());
            return;
        }

        if (!isLocalHost(uri.getHost())) {
            logger.info("Skipping llama-server [{}]: URL host '{}' is not local.", spec.name(), uri.getHost());
            return;
        }

        int port = uri.getPort();
        if (isPortOpen(uri.getHost(), port, 300)) {
            logger.info("Skipping llama-server [{}]: port {} is already in use.", spec.name(), port);
            return;
        }

        String modelArg = spec.model().endsWith(".gguf") ? spec.model() : spec.model() + ".gguf";
        List<String> command = new ArrayList<>();
        command.add(llamaCommand);
        command.add("-m");
        command.add(modelArg);
        if (StringUtils.hasText(modelsDir)) {
            command.add("--models-dir");
            command.add(modelsDir);
        }
        if (spec.embedding()) {
            command.add("--embeddings");
        }
        if (spec.reranking()) {
            command.add("--reranking");
        }
        command.add("--ctx-size");
        command.add(String.valueOf(ctxSize));
        command.add("--batch-size");
        command.add(String.valueOf(batchSize));
        command.add("--ubatch-size");
        command.add(String.valueOf(ubatchSize));
        command.add("--parallel");
        command.add(String.valueOf(parallel));
        command.add("--host");
        command.add(llamaHost);
        command.add("--port");
        command.add(String.valueOf(port));

        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .start();

            if (!waitForPort(uri.getHost(), port, startTimeoutMs)) {
                logger.warn("llama-server [{}] did not become ready within {} ms.", spec.name(), startTimeoutMs);
            } else {
                logger.info("Started llama-server [{}] on port {}.", spec.name(), port);
            }
            managedProcesses.add(new ManagedProcess(spec.name(), port, process));
        } catch (IOException e) {
            logger.error("Failed to start llama-server [{}]. Command: {}", spec.name(), String.join(" ", command), e);
        }
    }

    private boolean waitForPort(String host, int port, long timeoutMs) {
        Instant deadline = Instant.now().plus(Duration.ofMillis(timeoutMs));
        while (Instant.now().isBefore(deadline)) {
            if (isPortOpen(host, port, 300)) {
                return true;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean isConfiguredValue(String value) {
        return StringUtils.hasText(value) && !"none".equalsIgnoreCase(value.trim());
    }

    private boolean isLocalHost(String host) {
        if (!StringUtils.hasText(host)) {
            return true;
        }
        String normalized = host.trim().toLowerCase();
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "0.0.0.0".equals(normalized)
                || "::1".equals(normalized);
    }

    private record ServerSpec(String name, String provider, String url, String model, boolean embedding,
            boolean reranking) {
    }

    private record ManagedProcess(String name, int port, Process process) {
    }
}