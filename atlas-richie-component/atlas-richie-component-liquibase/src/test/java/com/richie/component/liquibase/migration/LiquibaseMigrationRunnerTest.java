/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
