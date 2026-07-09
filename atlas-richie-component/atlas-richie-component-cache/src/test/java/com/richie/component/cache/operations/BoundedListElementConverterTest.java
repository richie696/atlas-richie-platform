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
package com.richie.component.cache.operations;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedListElementConverterTest {

    @Test
    void convertAll_skipsNullAndInvalidElements() {
        var raw = new ArrayList<Object>();
        raw.add("ok");
        raw.add(null);
        raw.add("tail");
        List<String> result = BoundedListElementConverter.convertAll(
                raw,
                "test:queue",
                String.class,
                "drain");

        assertEquals(2, result.size());
        assertEquals(List.of("ok", "tail"), result);
    }

    @Test
    void convertOne_returnsNullWithoutWarnWhenRawIsNull() {
        assertNull(BoundedListElementConverter.convertOne(null, "k", String.class, "peek"));
    }

    @Test
    void convertOne_warnsAndReturnsNullOnDeserializeFailure() {
        assertNull(BoundedListElementConverter.convertOne("bad", "test:q", Integer.class, "poll"));
    }

    @Test
    void convertOne_returnsValueOnSuccess() {
        assertEquals("ok", BoundedListElementConverter.convertOne("ok", "test:q", String.class, "poll"));
    }

    @Test
    void convertAll_returnsEmptyWhenAllInvalid() {
        List<Integer> result = BoundedListElementConverter.convertAll(
                List.of("not-a-number"),
                "test:stack",
                Integer.class,
                "latest");

        assertTrue(result.isEmpty());
    }
}
