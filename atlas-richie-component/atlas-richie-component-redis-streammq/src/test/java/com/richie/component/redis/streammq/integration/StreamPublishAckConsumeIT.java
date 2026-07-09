/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.redis.streammq.integration;

import com.richie.component.redis.streammq.StreamMQ;
import com.richie.component.redis.streammq.function.StreamFunction;
import com.richie.component.redis.streammq.stream.RedisStreamReactor;
import com.richie.component.redis.streammq.stream.StreamMessageEvent;
import com.richie.component.redis.streammq.support.AbstractStreammqRedisIntegrationTest;
import com.richie.component.redis.streammq.support.ItStreamPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamPublishAckConsumeIT extends AbstractStreammqRedisIntegrationTest {

    private static final String STREAM_KEY = "it:stream:wave-c";
    private static final String GROUP = "it-group";
    private static final String CONSUMER = "it-consumer-1";

    @Autowired
    private StreamFunction streamFunction;

    @Autowired
    private RedisStreamReactor redisStreamReactor;

    @AfterEach
    void stopPoller() {
        redisStreamReactor.stopPolling(STREAM_KEY, GROUP, CONSUMER);
    }

    @Test
    void publishAckConsume_shouldCompleteHappyPath() throws InterruptedException {
        ensureConsumerGroup();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<StreamMessageEvent<?>> received = new AtomicReference<>();
        var subscription = streamFunction.messageFlow()
                .filter(event -> STREAM_KEY.equals(event.streamKey()))
                .doOnNext(event -> {
                    received.set(event);
                    latch.countDown();
                })
                .subscribe();

        redisStreamReactor.startPolling(STREAM_KEY, GROUP, CONSUMER, 10, 500L, ItStreamPayload.class);

        String recordId = streamFunction.publish(STREAM_KEY, new ItStreamPayload("wave-c-payload"));
        assertThat(recordId).isNotBlank();

        assertTrue(latch.await(20, TimeUnit.SECONDS), "expected stream message within timeout");

        StreamMessageEvent<?> event = received.get();
        assertThat(event).isNotNull();
        assertThat(event.recordId()).isNotNull();
        String payloadText = String.valueOf(event.payload());
        if (!payloadText.contains("wave-c-payload")) {
            payloadText = new String(Base64.getDecoder().decode(payloadText));
        }
        assertThat(payloadText).contains("wave-c-payload");

        streamFunction.acknowledge(STREAM_KEY, GROUP, event.recordId().getValue());

        assertPendingClearedWithin(Duration.ofSeconds(10));

        subscription.dispose();
    }

    @Test
    void streamMqFacade_shouldPublishThroughStaticEntry() {
        ensureConsumerGroup();
        String recordId = StreamMQ.stream().publish(STREAM_KEY, new ItStreamPayload("via-facade"));
        assertThat(recordId).isNotBlank();
        assertThat(stringRedisTemplate.opsForStream().size(STREAM_KEY)).isGreaterThan(0L);
    }

    private void assertPendingClearedWithin(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            long pending = stringRedisTemplate.opsForStream().pending(STREAM_KEY, GROUP).getTotalPendingMessages();
            if (pending == 0L) {
                return;
            }
            Thread.sleep(250L);
        }
        long pending = stringRedisTemplate.opsForStream().pending(STREAM_KEY, GROUP).getTotalPendingMessages();
        assertThat(pending).as("pending messages after ack").isZero();
    }

    private void ensureConsumerGroup() {
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(STREAM_KEY))) {
            streamFunction.publish(STREAM_KEY, new ItStreamPayload("bootstrap"));
        }
        try {
            stringRedisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.latest(), GROUP);
        } catch (Exception ignored) {
            // 组已存在时忽略
        }
    }
}
