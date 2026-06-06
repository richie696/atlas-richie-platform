package com.richie.component.liquibase.migration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeLogRegistryTest {

    @Test
    void add_ignoresBlankAndDeduplicates() {
        ChangeLogRegistry registry = new ChangeLogRegistry();

        registry.add(null);
        registry.add("  ");
        registry.add("classpath:db/changelog/a.yaml");
        registry.add("classpath:db/changelog/a.yaml");

        assertThat(registry.isEmpty()).isFalse();
        assertThat(registry.getAll()).containsExactly("classpath:db/changelog/a.yaml");
    }
}
