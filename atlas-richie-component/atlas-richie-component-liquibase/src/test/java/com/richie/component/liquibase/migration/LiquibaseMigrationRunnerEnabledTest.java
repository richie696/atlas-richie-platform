package com.richie.component.liquibase.migration;

import com.richie.component.liquibase.config.LiquibaseProperties;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LiquibaseMigrationRunnerEnabledTest {

    @Test
    void afterSingletonsInstantiated_noOpWhenRegistryEmptyAndScanDisabled() {
        DataSource dataSource = mock(DataSource.class);
        LiquibaseProperties properties = new LiquibaseProperties();
        properties.setEnable(true);
        properties.setEnableScan(false);
        LiquibaseMigrationRunner runner = new LiquibaseMigrationRunner(
                dataSource, properties, new ChangeLogRegistry(), new ChangeLogResolver());

        runner.afterSingletonsInstantiated();

        verifyNoInteractions(dataSource);
    }
}
