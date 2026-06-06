package com.richie.component.liquibase.migration;

import com.richie.component.liquibase.config.LiquibaseProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiquibaseMigrationRunnerScanIT {

    @Test
    void afterSingletonsInstantiated_mergesScannedChangeLogsInDryRun() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:liquibase-scan;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        LiquibaseProperties properties = new LiquibaseProperties();
        properties.setEnable(true);
        properties.setEnableScan(true);
        properties.setDryRun(true);
        ChangeLogResolver resolver = mock(ChangeLogResolver.class);
        when(resolver.resolveChangeLogs(any())).thenReturn(
                java.util.List.of("db/changelog/db.changelog-master.yaml"));
        LiquibaseMigrationRunner runner = new LiquibaseMigrationRunner(
                dataSource, properties, new ChangeLogRegistry(), resolver);

        assertThatCode(runner::afterSingletonsInstantiated).doesNotThrowAnyException();
    }
}
