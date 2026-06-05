package com.richie.component.cache.integration;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.support.AbstractRedisIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedQueueIT extends AbstractRedisIntegrationTest {

    record Task(String id) {
    }

    @Test
    void offerPoll_fifoOrder() {
        var queue = GlobalCache.queue().create("it:bq:fifo", 10, Task.class);
        queue.offer(new Task("t1"));
        queue.offer(new Task("t2"));
        assertThat(queue.poll().id()).isEqualTo("t1");
        assertThat(queue.poll().id()).isEqualTo("t2");
        GlobalCache.queue().destroy("it:bq:fifo");
    }

    @Test
    void offer_shouldEvictOldestWhenFull() {
        var queue = GlobalCache.queue().create("it:bq:evict", 2, Task.class);
        queue.offer(new Task("old"));
        queue.offer(new Task("mid"));
        queue.offer(new Task("new"));
        assertThat(queue.size()).isEqualTo(2L);
        assertThat(queue.poll().id()).isEqualTo("mid");
        assertThat(queue.poll().id()).isEqualTo("new");
        GlobalCache.queue().destroy("it:bq:evict");
    }

    @Test
    void drain_shouldBatchPop() {
        var queue = GlobalCache.queue().getOrCreate("it:bq:drain", 5, Task.class);
        queue.offer(new Task("a"));
        queue.offer(new Task("b"));
        queue.offer(new Task("c"));
        List<Task> batch = queue.drain(2);
        assertThat(batch).extracting(Task::id).containsExactly("a", "b");
        assertThat(queue.size()).isEqualTo(1L);
        GlobalCache.queue().destroy("it:bq:drain");
    }

    @Test
    void grow_shouldDoubleMaxLen() {
        var queue = GlobalCache.queue().create("it:bq:grow", 100, Task.class);
        assertThat(queue.grow()).isTrue();
        assertThat(queue.getMaxLen()).isEqualTo(200L);
        GlobalCache.queue().destroy("it:bq:grow");
    }
}
