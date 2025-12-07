package io.github.bzkf.obdstofhir;

import io.github.bzkf.obdstofhir.config.RecordIdDbConfig;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(
    name = "fhir.mappings.patient-reference-generation.strategy",
    havingValue = "RECORD_ID_DATABASE_LOOKUP")
@EnableConfigurationProperties(RecordIdDbConfig.class)
public class RecordIdDbConfigAutoConfiguration {

  @Bean
  public DataSource recordIdDataSource(RecordIdDbConfig cfg) {
    return DataSourceBuilder.create()
        .url(cfg.jdbcUrl())
        .username(cfg.username())
        .password(cfg.password())
        .build();
  }

  @Bean
  public JdbcTemplate recordIdJdbcTemplate(DataSource recordIdDataSource) {
    return new JdbcTemplate(recordIdDataSource);
  }
}
