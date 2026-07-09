/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.http.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SseLineParser} 单元测试，覆盖 SSE 协议逐行解析的所有分支。
 */
class SseLineParserTest {

    @Test
    void blankLineDispatchesCompleteDataOnlyEvent() {
        SseLineParser parser = new SseLineParser();

        assertThat(parser.feed("data: hello")).isNull();
        SseEvent event = parser.feed("");

        assertThat(event).isNotNull();
        assertThat(event.id()).isNull();
        assertThat(event.event()).isEqualTo(SseEvent.DEFAULT_EVENT_NAME);
        assertThat(event.data()).isEqualTo("hello");
        assertThat(event.retry()).isNull();
    }

    @Test
    void blankLineWithNoBufferReturnsNull() {
        SseLineParser parser = new SseLineParser();

        assertThat(parser.feed("")).isNull();
        assertThat(parser.feed("")).isNull();
    }

    @Test
    void nullLineDispatchesPendingEvent() {
        SseLineParser parser = new SseLineParser();
        parser.feed("data: tail");

        SseEvent event = parser.feed(null);

        assertThat(event).isNotNull();
        assertThat(event.data()).isEqualTo("tail");
    }

    @Test
    void commentLineIsIgnored() {
        SseLineParser parser = new SseLineParser();

        assertThat(parser.feed(":heartbeat")).isNull();
        assertThat(parser.feed(":")).isNull();
        assertThat(parser.feed("data: real")).isNull();

        SseEvent event = parser.feed("");
        assertThat(event).isNotNull();
        assertThat(event.data()).isEqualTo("real");
        assertThat(parser.flush()).isNull();
    }

    @Test
    void fieldWithoutColonTreatedAsFieldNameWithEmptyValue() {
        SseLineParser parser = new SseLineParser();

        assertThat(parser.feed("data")).isNull();
        assertThat(parser.feed("data: real")).isNull();

        SseEvent event = parser.feed("");
        assertThat(event).isNotNull();
        assertThat(event.data()).isEqualTo("real");
    }

    @Test
    void colonValueKeepsLeadingSpaceWhenNotSingleSpace() {
        SseLineParser parser = new SseLineParser();

        parser.feed("data: spaced");
        SseEvent event = parser.feed("");

        assertThat(event.data()).isEqualTo("spaced");
    }

    @Test
    void multiLineDataJoinsWithNewline() {
        SseLineParser parser = new SseLineParser();

        parser.feed("data: line1");
        parser.feed("data: line2");
        parser.feed("data: line3");
        SseEvent event = parser.feed("");

        assertThat(event.data()).isEqualTo("line1\nline2\nline3");
    }

    @Test
    void retryFieldAcceptsPositiveInteger() {
        SseLineParser parser = new SseLineParser();

        parser.feed("retry: 3000");
        SseEvent event = parser.feed("");

        assertThat(event.retry()).isEqualTo(Duration.ofMillis(3000));
    }

    @Test
    void retryFieldIgnoresZeroAndNegative() {
        SseLineParser parser = new SseLineParser();

        parser.feed("retry: 0");
        parser.feed("data: x");
        SseEvent event = parser.feed("");

        assertThat(event).isNotNull();
        assertThat(event.retry()).isNull();
    }

    @Test
    void retryFieldIgnoresNonNumeric() {
        SseLineParser parser = new SseLineParser();

        parser.feed("retry: abc");
        parser.feed("data: x");
        SseEvent event = parser.feed("");

        assertThat(event).isNotNull();
        assertThat(event.retry()).isNull();
    }

    @Test
    void retryFieldIgnoresLeadingZerosAndSignedValues() {
        SseLineParser parser = new SseLineParser();

        parser.feed("retry: 0123");
        parser.feed("retry: -5");
        parser.feed("data: real");
        SseEvent event = parser.feed("");

        assertThat(event).isNotNull();
        assertThat(event.retry()).isNull();
    }

    @Test
    void idEventAndRetryAreResettableBetweenEvents() {
        SseLineParser parser = new SseLineParser();

        parser.feed("id: 1");
        parser.feed("event: update");
        parser.feed("retry: 1500");
        parser.feed("data: alpha");
        SseEvent first = parser.feed("");

        parser.feed("data: beta");
        SseEvent second = parser.feed("");

        assertThat(first.id()).isEqualTo("1");
        assertThat(first.event()).isEqualTo("update");
        assertThat(first.data()).isEqualTo("alpha");
        assertThat(first.retry()).isEqualTo(Duration.ofMillis(1500));

        assertThat(second.id()).isNull();
        assertThat(second.event()).isEqualTo(SseEvent.DEFAULT_EVENT_NAME);
        assertThat(second.data()).isEqualTo("beta");
        assertThat(second.retry()).isNull();
    }

    @Test
    void lastValueWinsForRepeatedScalarFields() {
        SseLineParser parser = new SseLineParser();

        parser.feed("id: 100");
        parser.feed("id: 200");
        parser.feed("event: first");
        parser.feed("event: second");
        SseEvent event = parser.feed("");

        assertThat(event.id()).isEqualTo("200");
        assertThat(event.event()).isEqualTo("second");
    }

    @Test
    void unknownFieldIsIgnored() {
        SseLineParser parser = new SseLineParser();

        parser.feed("foo: bar");
        parser.feed("data: real");
        SseEvent event = parser.feed("");

        assertThat(event.data()).isEqualTo("real");
    }

    @Test
    void explicitEmptyEventFieldFallsBackToDefaultName() {
        SseLineParser parser = new SseLineParser();

        parser.feed("event: ");
        parser.feed("data: x");
        SseEvent event = parser.feed("");

        assertThat(event.event()).isEqualTo(SseEvent.DEFAULT_EVENT_NAME);
    }

    @Test
    void colonAtEndOfLineAppendsEmptySegmentThenDispatchesWithTrailingNewline() {
        SseLineParser parser = new SseLineParser();

        parser.feed("data: line1");
        parser.feed("data:");
        SseEvent event = parser.feed("");

        assertThat(event).isNotNull();
        assertThat(event.data()).isEqualTo("line1\n");
    }

    @Test
    void colonWithoutLeadingSpaceKeepsSpaceInValue() {
        SseLineParser parser = new SseLineParser();

        parser.feed("data:two spaces");
        SseEvent event = parser.feed("");

        assertThat(event.data()).isEqualTo("two spaces");
    }

    @Test
    void fullRoundTripProducesExpectedEvent() {
        SseLineParser parser = new SseLineParser();

        parser.feed(":keepalive");
        parser.feed("id: 42");
        parser.feed("event: message");
        parser.feed("data: first");
        parser.feed("data: second");
        parser.feed("retry: 2500");
        SseEvent event = parser.feed("");

        assertThat(event.id()).isEqualTo("42");
        assertThat(event.event()).isEqualTo("message");
        assertThat(event.data()).isEqualTo("first\nsecond");
        assertThat(event.retry()).isEqualTo(Duration.ofMillis(2500));
    }

    @Test
    void flushReturnsPendingEventOrNull() {
        SseLineParser parser = new SseLineParser();

        assertThat(parser.flush()).isNull();

        parser.feed("data: late");
        assertThat(parser.flush()).satisfies(e -> {
            assertThat(e.data()).isEqualTo("late");
        });
        assertThat(parser.flush()).isNull();
    }

    @Test
    void retryWithMultipleEventsOnlyLastValidApplied() {
        SseLineParser parser = new SseLineParser();

        parser.feed("data: a");
        parser.feed("retry: bad");
        SseEvent first = parser.feed("");

        parser.feed("retry: 900");
        parser.feed("data: b");
        SseEvent second = parser.feed("");

        assertThat(first.retry()).isNull();
        assertThat(second.retry()).isEqualTo(Duration.ofMillis(900));
    }

    private static SseEvent feed(SseLineParser parser, String... lines) {
        for (String line : lines) {
            SseEvent ev = parser.feed(line);
            if (ev != null) {
                return ev;
            }
        }
        return null;
    }

}