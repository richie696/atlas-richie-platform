package com.richie.component.liquibase.migration;

import com.richie.component.liquibase.config.LiquibaseProperties;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LiquibaseMigrationRunnerTest {

    @Test
    void afterSingletonsInstantiated_skipsWhenDisabled() {
        DataSource dataSource = mock(DataSource.class);
        LiquibaseProperties properties = new LiquibaseProperties();
        properties.setEnable(false);
        ChangeLogRegistry registry = new ChangeLogRegistry();
        registry.add("classpath:db/changelog/db.changelog-master.yaml");
        LiquibaseMigrationRunner runner = new LiquibaseMigrationRunner(
                dataSource, properties, registry, new ChangeLogResolver());

        runner.afterSingletonsInstantiated();

        verifyNoInteractions(dataSource);
    }
}
