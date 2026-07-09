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
package com.richie.context.bloom;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuavaBloomFilterTest {

    @Test
    void mightContain_returnsFalseBeforePut() {
        GuavaBloomFilter bf = new GuavaBloomFilter(new BloomFilterProperties());
        assertFalse(bf.mightContain("nope"));
    }

    @Test
    void mightContain_returnsTrueAfterPut() {
        GuavaBloomFilter bf = new GuavaBloomFilter(new BloomFilterProperties());
        bf.put("user:1");
        assertTrue(bf.mightContain("user:1"));
    }

    @Test
    void putAll_insertsBatch() {
        GuavaBloomFilter bf = new GuavaBloomFilter(new BloomFilterProperties());
        bf.putAll(Set.of("a", "b", "c"));
        assertTrue(bf.mightContain("a"));
        assertTrue(bf.mightContain("b"));
        assertTrue(bf.mightContain("c"));
        assertFalse(bf.mightContain("d"));
    }

    @Test
    void isExists_returnsTrueAfterConstruction() {
        GuavaBloomFilter bf = new GuavaBloomFilter(new BloomFilterProperties());
        assertTrue(bf.isExists());
    }

    @Test
    void customExpectedInsertionsAndProbability_areApplied() {
        BloomFilterProperties props = new BloomFilterProperties();
        props.setExpectedInsertions(1000);
        props.setFalseProbability(0.001);
        GuavaBloomFilter bf = new GuavaBloomFilter(props);
        bf.put("x");
        assertTrue(bf.mightContain("x"));
        assertTrue(bf.isExists());
    }
}