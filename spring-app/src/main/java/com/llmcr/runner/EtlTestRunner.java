package com.llmcr.runner;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

import com.llmcr.entity.TrackRoot;
import com.llmcr.entity.Source.SourceType;
import com.llmcr.repository.TrackRootRepository;
import com.llmcr.service.SourceService;
import com.llmcr.service.etl.ETLPipeline;

@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "etl")
public class EtlTestRunner implements CommandLineRunner {
	@Autowired
	private final TrackRootRepository trackRootRepository;

	@Autowired
	private final SourceService sourceService;

	@Autowired
	private final ETLPipeline etlPipeline;

	@Autowired
	private final JdbcTemplate jdbcTemplate;

	public EtlTestRunner(TrackRootRepository trackRootRepository, SourceService sourceService,
			ETLPipeline etlPipeline, JdbcTemplate jdbcTemplate) {
		this.trackRootRepository = trackRootRepository;
		this.sourceService = sourceService;
		this.etlPipeline = etlPipeline;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(String... args) throws Exception {
		resetEntityTables();

		Map<String, List<SourceType>> trackRootConfig = Map.of(
				"../_datasets/test/spring-ai-main/",
				List.of(SourceType.JAVACODE),
				"../_datasets/test/spring-ai-main/spring-ai-docs/src/main/antora/modules/ROOT/pages/",
				List.of(SourceType.MARKDOWN, SourceType.ASCIIDOC),
				"../_datasets/test/docs/",
				List.of(SourceType.PDF, SourceType.JSON));

		trackRootConfig.forEach((path, sourceTypes) -> {
			trackRootRepository.save(new TrackRoot(path, sourceTypes));
		});

		sourceService.refreshTrackRoots();

		etlPipeline.run();
	}

	private void resetEntityTables() {
		jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
		try {
			jdbcTemplate.execute("TRUNCATE TABLE chunk_index");
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
