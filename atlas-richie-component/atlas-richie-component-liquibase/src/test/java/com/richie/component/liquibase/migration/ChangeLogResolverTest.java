package com.richie.component.liquibase.migration;

import com.richie.component.liquibase.config.LiquibaseProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeLogResolverTest {

    private final ChangeLogResolver resolver = new ChangeLogResolver();

    @Test
    void resolveChangeLogs_usesDefaultWhenPatternsResolveEmpty() {
        LiquibaseProperties properties = new LiquibaseProperties();
        properties.setChangeLogs(List.of("classpath*:nonexistent/changelog/**/*.yaml"));

        List<String> resolved = resolver.resolveChangeLogs(properties);

        assertThat(resolved).containsExactly("db/changelog/db.changelog-master.yaml");
    }

    @Test
    void resolveChangeLogs_usesDefaultWhenChangeLogsEmpty() {
        LiquibaseProperties properties = new LiquibaseProperties();
        properties.setChangeLogs(List.of());

        List<String> resolved = resolver.resolveChangeLogs(properties);

        assertThat(resolved).containsExactly("db/changelog/db.changelog-master.yaml");
    }

    @Test
    void resolveChangeLogs_warnsWhenPatternInvalid() {
        LiquibaseProperties properties = new LiquibaseProperties();
        properties.setChangeLogs(List.of("classpath*:[invalid"));

        List<String> resolved = resolver.resolveChangeLogs(properties);

        assertThat(resolved).containsExactly("db/changelog/db.changelog-master.yaml");
    }
}
