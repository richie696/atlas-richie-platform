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
