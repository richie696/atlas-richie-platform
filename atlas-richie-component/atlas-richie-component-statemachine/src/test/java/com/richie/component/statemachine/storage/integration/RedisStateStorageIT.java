package com.richie.component.statemachine.storage.integration;

import com.richie.component.statemachine.context.StateContext;
import com.richie.component.statemachine.storage.StateHistory;
import com.richie.component.statemachine.storage.StateStorage;
import com.richie.component.statemachine.storage.support.AbstractStatemachineRedisIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedisStateStorageIT extends AbstractStatemachineRedisIntegrationTest {

    private static final String MACHINE = "order";
    private static final Long BUSINESS_ID = 9_001L;

    @Autowired
    private StateStorage stateStorage;

    @Test
    void saveAndGetCurrentState_roundTrip() {
        stateStorage.saveCurrentState(MACHINE, BUSINESS_ID, "PENDING", new StateContext("PENDING"));

        assertThat(stateStorage.getCurrentState(MACHINE, BUSINESS_ID)).isEqualTo("PENDING");
    }

    @Test
    void saveStateHistory_shouldPersistAndSortBySeqDesc() {
        StateContext first = contextWithMeta("alice", 1L);
        stateStorage.saveStateHistory(MACHINE, BUSINESS_ID, "PENDING", "CONFIRMED", "CONFIRM", first);

        StateContext second = contextWithMeta("bob", 2L);
        stateStorage.saveStateHistory(MACHINE, BUSINESS_ID, "CONFIRMED", "SHIPPED", "SHIP", second);

        List<StateHistory> histories = stateStorage.getStateHistory(MACHINE, BUSINESS_ID);

        assertThat(histories).hasSize(2);
        assertThat(histories.getFirst().getToState()).isEqualTo("SHIPPED");
        assertThat(histories.get(1).getToState()).isEqualTo("CONFIRMED");
    }

    @Test
    void deleteState_shouldRemoveCurrentStateAndHistoryList() {
        stateStorage.saveCurrentState(MACHINE, BUSINESS_ID, "PENDING", new StateContext("PENDING"));
        stateStorage.saveStateHistory(MACHINE, BUSINESS_ID, "PENDING", "CONFIRMED", "CONFIRM", new StateContext("PENDING"));

        stateStorage.deleteState(MACHINE, BUSINESS_ID);

        assertThat(stateStorage.getCurrentState(MACHINE, BUSINESS_ID)).isNull();
        assertThat(stateStorage.getStateHistory(MACHINE, BUSINESS_ID)).isEmpty();
    }

    private static StateContext contextWithMeta(String operator, long seq) {
        StateContext context = new StateContext("PENDING", "CONFIRM");
        context.setAttribute("operator", operator);
        context.setAttribute("remark", "it-case");
        context.setAttribute("seq", seq);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("channel", "it");
        context.setAttributes(attributes);
        return context;
    }
}
