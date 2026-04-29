package com.llmcr.runner;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

import com.llmcr.config.DatabaseInitializer;
import com.llmcr.service.SyncService;
import com.llmcr.service.etl.ETLPipeline;

@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "test")
public class TestRunner implements CommandLineRunner {
	@Autowired
	private final DatabaseInitializer databaseInitializer;

	@Autowired
	private final SyncService syncService;

	@Autowired
	private final ETLPipeline etlPipeline;

	@Autowired
	private final JdbcTemplate jdbcTemplate;

	public TestRunner(DatabaseInitializer databaseInitializer,
			SyncService syncService, ETLPipeline etlPipeline,
			JdbcTemplate jdbcTemplate) {
		this.databaseInitializer = databaseInitializer;
		this.syncService = syncService;
		this.etlPipeline = etlPipeline;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(String... args) throws Exception {
		// resetEntityTables();

		databaseInitializer.init();
		syncService.sync();
		etlPipeline.run();
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
		} finally {
			jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
		}
	}
}
