/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.ai.support.keypool;

import cn.richie696.component.ai.config.AiModelProperties;
import cn.richie696.component.ai.config.keypool.KeyPoolProperties;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyPoolManagerSecretRefreshTest {

    @Test
    void candidateGenerationIsInvisibleUntilCommitAndRollbackRestoresPrevious() {
        AiModelProperties current = properties();
        ApiKeyPoolManager manager = new ApiKeyPoolManager(current);
        ApiKeyPool previous = manager.getPool("chat", Set.of("old-a", "old-b"));

        var prepared = manager.prepareSecretRefresh(
                properties(), () -> manager.getPool("chat", Set.of("new-a", "new-b")));

        assertThat(manager.getPool("chat", Set.of("old-a", "old-b"))).isSameAs(previous);
        prepared.refresh().commit();
        assertThat(manager.getPool("chat", Set.of("new-a", "new-b"))).isSameAs(prepared.value());

        prepared.refresh().rollback();
        assertThat(manager.getPool("chat", Set.of("old-a", "old-b"))).isSameAs(previous);
        manager.closeAll();
    }

    private AiModelProperties properties() {
        AiModelProperties properties = new AiModelProperties();
        KeyPoolProperties keyPool = new KeyPoolProperties();
        keyPool.setEnabled(true);
        properties.setKeyPool(keyPool);
        return properties;
    }
}
