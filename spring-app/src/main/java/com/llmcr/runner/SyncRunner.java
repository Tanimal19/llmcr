package com.llmcr.runner;

import com.llmcr.service.sync.SourceSyncService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "sync")
public class SyncRunner implements CommandLineRunner {

    private final SourceSyncService syncService;
    private final JdbcTemplate jdbcTemplate;

    public SyncRunner(SourceSyncService syncService, JdbcTemplate jdbcTemplate) {
        this.syncService = syncService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        syncService.syncAllTrackRootSource();
    }

    private void resetEntityTables() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            jdbcTemplate.execute("TRUNCATE TABLE collection_have_chunks");
            jdbcTemplate.execute("TRUNCATE TABLE collection_have_track_roots");
            jdbcTemplate.execute("TRUNCATE TABLE chunk");
            jdbcTemplate.execute("TRUNCATE TABLE context");
            jdbcTemplate.execute("TRUNCATE TABLE source");
            jdbcTemplate.execute("TRUNCATE TABLE track_root");
            jdbcTemplate.execute("TRUNCATE TABLE chunk_collection");
            jdbcTemplate.execute("TRUNCATE TABLE track_root_allowed_source_types");
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }
}
