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
package com.richie.component.statemachine.config;

import com.richie.component.statemachine.config.properties.DbPersistenceMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StatePersistenceModeResolverTest {

    @Test
    void resolveMachineMode_shouldPreferDefinitionOverGlobalDefault() {
        StateMachineDefinition definition = new StateMachineDefinition();
        definition.setDbPersistenceMode(DbPersistenceMode.SYNC);

        DbPersistenceMode mode = StatePersistenceModeResolver.resolveMachineMode(definition, DbPersistenceMode.ASYNC);

        assertThat(mode).isEqualTo(DbPersistenceMode.SYNC);
    }

    @Test
    void resolveStateMode_shouldReturnEmptyWhenStateMissing() {
        StateMachineDefinition definition = new StateMachineDefinition();
        definition.setStates(List.of());

        Optional<DbPersistenceMode> mode = StatePersistenceModeResolver.resolveStateMode(definition, "PENDING");

        assertThat(mode).isEmpty();
    }

    @Test
    void resolveStateMode_shouldReturnConfiguredMode() {
        StateMachineDefinition definition = new StateMachineDefinition();
        StateMachineDefinition.StateDefinition pending = new StateMachineDefinition.StateDefinition();
        pending.setName("PENDING");
        pending.setStatePersistenceMode(DbPersistenceMode.SYNC);
        definition.setStates(List.of(pending));

        Optional<DbPersistenceMode> mode = StatePersistenceModeResolver.resolveStateMode(definition, "PENDING");

        assertThat(mode).contains(DbPersistenceMode.SYNC);
    }
}
