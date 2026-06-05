package com.richie.component.cache.integration;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.support.AbstractRedisIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedStackIT extends AbstractRedisIntegrationTest {

    record Event(String name) {
    }

    @Test
    void pushPop_lifoOrder() {
        var stack = GlobalCache.stack().create("it:stack:lifo", 10, Event.class);
        stack.push(new Event("first"));
        stack.push(new Event("second"));
        assertThat(stack.pop().name()).isEqualTo("second");
        assertThat(stack.pop().name()).isEqualTo("first");
        GlobalCache.stack().destroy("it:stack:lifo");
    }

    @Test
    void push_shouldRejectWhenFull() {
        var stack = GlobalCache.stack().create("it:stack:full", 2, Event.class);
        assertThat(stack.push(new Event("a"))).isTrue();
        assertThat(stack.push(new Event("b"))).isTrue();
        assertThat(stack.push(new Event("overflow"))).isFalse();
        assertThat(stack.size()).isEqualTo(2L);
        GlobalCache.stack().destroy("it:stack:full");
    }
}
