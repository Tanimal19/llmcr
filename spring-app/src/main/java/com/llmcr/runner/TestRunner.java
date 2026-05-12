package com.llmcr.runner;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.llmcr.repository.SourceRepository;

@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "test")
public class TestRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TestRunner.class);
    private static final Path SPRING_APP_ROOT = Path.of("").toAbsolutePath().normalize();

    private final SourceRepository sourceRepository;

    public TestRunner(SourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting source path normalization runner, SPRING_APP_ROOT={}", SPRING_APP_ROOT);

        sourceRepository.findAll().stream().forEach(source -> {
            try {
                String original = source.getPath();
                String relative = toRelativePath(original);
                log.info("Source id={}, original path: {}, relative path: {}",
                        source.getId(), original, relative);
                if (!original.equals(relative)) {
                    source.setPath(relative);
                    sourceRepository.save(source);
                }
            } catch (InvalidPathException e) {
                log.warn("Source id={} has invalid path: {}, skipping", source.getId(), source.getPath());
            }
        });
    }

    private String toRelativePath(String pathValue) {
        Path inputPath = Path.of(pathValue).toAbsolutePath().normalize();
        return SPRING_APP_ROOT.relativize(inputPath).toString();
    }
}
