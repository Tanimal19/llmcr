package com.llmcr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LlamaServerAutoStarter implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(LlamaServerAutoStarter.class);
    private static final List<Target> TARGETS = List.of(
            new Target("small-chat", "llmcr.chat.small", false, false),
            new Target("large-chat", "llmcr.chat.large", false, false),
            new Target("embedding", "llmcr.embedding", true, false),
            new Target("reranking", "llmcr.reranking", false, true));

    private final Environment environment;

    public LlamaServerAutoStarter(Environment environment) {
        this.environment = environment;
    }

    @Value("${llmcr.llama.autostart.enabled:true}")
    private boolean autoStartEnabled;

    @Value("${llmcr.llama.command:llama-server}")
    private String llamaCommand;

    @Value("${llmcr.llama.modeldir:}")
    private String modelDir;

    @Value("${llmcr.llama.ctx-size:4096}")
    private int ctxSize;

    @Value("${llmcr.llama.batch-size:512}")
    private int batchSize;

    @Value("${llmcr.llama.ubatch-size:128}")
    private int ubatchSize;

    @Value("${llmcr.llama.parallel:1}")
    private int parallel;

    @Value("${llmcr.llama.start-timeout-ms:15000}")
    private long startTimeoutMs;

    private final List<ManagedProcess> managedProcesses = Collections.synchronizedList(new ArrayList<>());
    private final LongAdder startAttemptCounter = new LongAdder();
    private final LongAdder startSuccessCounter = new LongAdder();
    private final LongAdder startFailureCounter = new LongAdder();
    private volatile boolean running;
    private volatile Thread shutdownHook;

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }

        running = true;
        if (!autoStartEnabled) {
            logger.debug("llama.cpp auto-start is disabled (llmcr.llama.autostart.enabled=false).");
            return;
        }

        registerShutdownHook();

        for (Target target : TARGETS) {
            startIfConfigured(buildServerSpec(target));
        }
    }

    @Override
    public synchronized void stop() {
        removeShutdownHook();

        for (ManagedProcess managedProcess : new ArrayList<>(managedProcesses)) {
            Process process = managedProcess.process();
            if (!process.isAlive()) {
                continue;
            }

            logger.debug("Stopping llama-server [{}] on port {}.", managedProcess.name(), managedProcess.port());
            process.destroy();
            try {
                boolean exited = process.waitFor(3, TimeUnit.SECONDS);
                if (!exited) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }

        managedProcesses.clear();
        logger.debug("llama-server autostarter metrics starts_attempted={} starts_succeeded={} starts_failed={}",
                startAttemptCounter.sum(),
                startSuccessCounter.sum(),
                startFailureCounter.sum());
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
            logger.debug("Skipping llama-server [{}]: invalid URL '{}'", spec.name(), spec.url());
            return;
        }

        if (uri.getPort() <= 0) {
            logger.debug("Skipping llama-server [{}]: URL must include a port: {}", spec.name(), spec.url());
            return;
        }

        if (!isAllowedConnectHost(uri.getHost())) {
            logger.debug("Skipping llama-server [{}]: URL host '{}' is not local.", spec.name(), uri.getHost());
            return;
        }

        String modelArg = resolveModelArg(spec.model());

        int port = uri.getPort();
        List<String> command = new ArrayList<>();
        command.add(llamaCommand);
        command.add("-m");
        command.add(modelArg);
        if (spec.embedding()) {
            command.add("--embeddings");
        }
        if (spec.reranking()) {
            command.add("--reranking");
        }
        command.add("--ctx-size");
        command.add(String.valueOf(spec.runtimeConfig().ctxSize()));
        command.add("--batch-size");
        command.add(String.valueOf(spec.runtimeConfig().batchSize()));
        command.add("--ubatch-size");
        command.add(String.valueOf(spec.runtimeConfig().ubatchSize()));
        command.add("--parallel");
        command.add(String.valueOf(spec.runtimeConfig().parallel()));
        command.add("--port");
        command.add(String.valueOf(port));

        startAttemptCounter.increment();
        Instant startAt = Instant.now();
        try {
            logger.info("Starting llama-server [{}] on port {}.", spec.name(), port);
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            streamLogs(process.getInputStream(), spec.name());
            managedProcesses.add(new ManagedProcess(spec.name(), port, process));

            if (!waitForPort(uri.getHost(), port, process, startTimeoutMs)) {
                startFailureCounter.increment();
                if (process.isAlive()) {
                    logger.error("llama-server [{}] did not become ready within {} ms.",
                            spec.name(), startTimeoutMs);
                } else {
                    logger.error("llama-server [{}] exited before becoming ready (exit code {}).",
                            spec.name(), process.exitValue());
                }
            } else {
                startSuccessCounter.increment();
                long latencyMs = Duration.between(startAt, Instant.now()).toMillis();
                logger.info("Started llama-server [{}] on port {}.", spec.name(), port);
                logger.debug(
                        "llama_server_start name={} port={} startup_latency_ms={} starts_attempted={} starts_succeeded={} starts_failed={}",
                        spec.name(),
                        port,
                        latencyMs,
                        startAttemptCounter.sum(),
                        startSuccessCounter.sum(),
                        startFailureCounter.sum());
            }
        } catch (IOException e) {
            startFailureCounter.increment();
            logger.error("Failed to start llama-server [{}]. Command: {}", spec.name(), String.join(" ", command), e);
        }
    }

    private boolean waitForPort(String host, int port, Process process, long timeoutMs) {
        Instant deadline = Instant.now().plus(Duration.ofMillis(timeoutMs));
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                return false;
            }

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

    private String resolveModelArg(String model) {
        String trimmed = model.trim();
        String modelFileName = trimmed.endsWith(".gguf") ? trimmed : trimmed + ".gguf";
        if (!StringUtils.hasText(modelDir)) {
            return modelFileName;
        }
        return Path.of(modelDir.trim(), modelFileName).toString();
    }

    private RuntimeConfig resolveRuntimeConfig(String serverName,
            String propertyPrefix) {
        String ctxSizeOverride = environment.getProperty(propertyPrefix + ".llama.ctx-size");
        int resolvedCtxSize = parsePositiveIntOrDefault("ctx-size", serverName, ctxSizeOverride, ctxSize);
        return new RuntimeConfig(resolvedCtxSize, batchSize, ubatchSize, parallel);
    }

    private ServerSpec buildServerSpec(Target target) {
        String prefix = target.propertyPrefix();
        String provider = environment.getProperty(prefix + ".provider", "openai");
        String url = environment.getProperty(prefix + ".openai.url", "");
        String model = environment.getProperty(prefix + ".model", "");
        RuntimeConfig runtimeConfig = resolveRuntimeConfig(target.name(), prefix);
        return new ServerSpec(target.name(), provider, url, model, target.embedding(), target.reranking(),
                runtimeConfig);
    }

    private int parsePositiveIntOrDefault(String propertyName, String serverName, String configuredValue,
            int defaultValue) {
        if (!StringUtils.hasText(configuredValue)) {
            return defaultValue;
        }

        try {
            int parsed = Integer.parseInt(configuredValue.trim());
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }

        logger.warn("Invalid llama override {} for {}: '{}', fallback to {}.",
                propertyName, serverName, configuredValue, defaultValue);
        return defaultValue;
    }

    private void streamLogs(InputStream inputStream, String name) {
        Thread logThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (isErrorLogLine(line)) {
                        logger.error("llama_server_log name={} msg={}", name, line);
                    } else {
                        logger.info("llama_server_log name={} msg={}", name, line);
                    }
                }
            } catch (IOException e) {
                logger.error("Log stream error for {}", name, e);
            }
        }, "llama-log-" + name);
        logThread.setDaemon(true);
        logThread.start();
    }

    private boolean isErrorLogLine(String line) {
        if (!StringUtils.hasText(line)) {
            return false;
        }
        String normalized = line.toLowerCase();
        return normalized.contains("error")
                || normalized.contains("fatal")
                || normalized.contains("fail");
    }

    private void registerShutdownHook() {
        if (shutdownHook != null) {
            return;
        }

        shutdownHook = new Thread(() -> {
            try {
                stop();
            } catch (Exception e) {
                logger.warn("Failed during llama-server shutdown hook execution.", e);
            }
        }, "llama-server-shutdown-hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private void removeShutdownHook() {
        Thread hook = shutdownHook;
        if (hook == null) {
            return;
        }

        shutdownHook = null;
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down.
        }
    }

    private boolean isAllowedConnectHost(String host) {
        if (!StringUtils.hasText(host)) {
            return true;
        }
        String normalized = host.trim().toLowerCase();
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized);
    }

    private record ServerSpec(String name,
            String provider,
            String url,
            String model,
            boolean embedding,
            boolean reranking,
            RuntimeConfig runtimeConfig) {
    }

    private record RuntimeConfig(int ctxSize,
            int batchSize,
            int ubatchSize,
            int parallel) {
    }

    private record ManagedProcess(String name, int port, Process process) {
    }

    private record Target(String name, String propertyPrefix, boolean embedding, boolean reranking) {
    }
}