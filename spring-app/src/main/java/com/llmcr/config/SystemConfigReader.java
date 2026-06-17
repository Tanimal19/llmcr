package com.llmcr.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class SystemConfigReader {

  private final String configFilePath;
  private final ObjectMapper objectMapper;

  public SystemConfigReader(@Value("${config.path}") String configFilePath) {
    this.configFilePath = configFilePath;
    this.objectMapper = new ObjectMapper(new YAMLFactory());
    this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public String getConfigFilePath() {
    return configFilePath;
  }

  @Bean
  public SystemConfig applicationConfiguration() {
    try {
      return objectMapper.readValue(new File(configFilePath), SystemConfig.class);
    } catch (IOException e) {
      throw new RuntimeException("Unable to read config: " + configFilePath, e);
    }
  }
}
