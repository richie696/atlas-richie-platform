package com.richie.component.liquibase.migration;

import com.richie.component.liquibase.config.LiquibaseProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThatCode;

class LiquibaseMigrationRunnerDryRunIT {

    @Test
    void dryRun_executesWithoutApplyingChanges() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:liquibase-dry;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        LiquibaseProperties properties = new LiquibaseProperties();
        properties.setEnable(true);
        properties.setDryRun(true);
        ChangeLogRegistry registry = new ChangeLogRegistry();
        registry.add("db/changelog/db.changelog-master.yaml");
        LiquibaseMigrationRunner runner = new LiquibaseMigrationRunner(
                dataSource, properties, registry, new ChangeLogResolver());

        assertThatCode(runner::afterSingletonsInstantiated).doesNotThrowAnyException();
    }
}
