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
package com.richie.component.mongodb.circuitbreaker;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MongodbCircuitBreakerPropertiesTest {

    @Test
    void defaults_shouldHaveCorrectValues() {
        MongodbCircuitBreakerProperties props = new MongodbCircuitBreakerProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getNacosDataId()).isEqualTo("mongodb-sentinel-degrade-rules");
        assertThat(props.getNacosGroup()).isEqualTo("DEFAULT_GROUP");
        assertThat(props.getMaxRt()).isEqualTo(100L);
        assertThat(props.getSlowRatioThreshold()).isEqualTo(0.5);
        assertThat(props.getTimeWindow()).isEqualTo(10);
        assertThat(props.getMinRequestAmount()).isEqualTo(10);
        assertThat(props.getStatIntervalMs()).isEqualTo(1000L);
    }

    @Test
    void setEnabled_shouldUpdateValue() {
        MongodbCircuitBreakerProperties props = new MongodbCircuitBreakerProperties();
        props.setEnabled(false);
        assertThat(props.isEnabled()).isFalse();
    }

    @Test
    void setNacosDataId_shouldUpdateValue() {
        MongodbCircuitBreakerProperties props = new MongodbCircuitBreakerProperties();
        props.setNacosDataId("custom-data-id");
        assertThat(props.getNacosDataId()).isEqualTo("custom-data-id");
    }

    @Test
    void setNacosGroup_shouldUpdateValue() {
        MongodbCircuitBreakerProperties props = new MongodbCircuitBreakerProperties();
        props.setNacosGroup("CUSTOM_GROUP");
        assertThat(props.getNacosGroup()).isEqualTo("CUSTOM_GROUP");
    }

    @Test
    void setMaxRt_shouldUpdateValue() {
        MongodbCircuitBreakerProperties props = new MongodbCircuitBreakerProperties();
        props.setMaxRt(200L);
        assertThat(props.getMaxRt()).isEqualTo(200L);
    }

    @Test
    void setSlowRatioThreshold_shouldUpdateValue() {
        MongodbCircuitBreakerProperties props = new MongodbCircuitBreakerProperties();
        props.setSlowRatioThreshold(0.8);
        assertThat(props.getSlowRatioThreshold()).isEqualTo(0.8);
    }

    @Test
    void setTimeWindow_shouldUpdateValue() {
        MongodbCircuitBreakerProperties props = new MongodbCircuitBreakerProperties();
        props.setTimeWindow(20);
        assertThat(props.getTimeWindow()).isEqualTo(20);
    }

    @Test
    void setMinRequestAmount_shouldUpdateValue() {
        MongodbCircuitBreakerProperties props = new MongodbCircuitBreakerProperties();
        props.setMinRequestAmount(5);
        assertThat(props.getMinRequestAmount()).isEqualTo(5);
    }

    @Test
    void setStatIntervalMs_shouldUpdateValue() {
        MongodbCircuitBreakerProperties props = new MongodbCircuitBreakerProperties();
        props.setStatIntervalMs(2000L);
        assertThat(props.getStatIntervalMs()).isEqualTo(2000L);
    }

    @Test
    void allSetters_shouldWorkCorrectly() {
        MongodbCircuitBreakerProperties props = new MongodbCircuitBreakerProperties();
        props.setEnabled(false);
        props.setNacosDataId("custom-data-id");
        props.setNacosGroup("CUSTOM_GROUP");
        props.setMaxRt(200L);
        props.setSlowRatioThreshold(0.8);
        props.setTimeWindow(20);
        props.setMinRequestAmount(5);
        props.setStatIntervalMs(2000L);

        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getNacosDataId()).isEqualTo("custom-data-id");
        assertThat(props.getNacosGroup()).isEqualTo("CUSTOM_GROUP");
        assertThat(props.getMaxRt()).isEqualTo(200L);
        assertThat(props.getSlowRatioThreshold()).isEqualTo(0.8);
        assertThat(props.getTimeWindow()).isEqualTo(20);
        assertThat(props.getMinRequestAmount()).isEqualTo(5);
        assertThat(props.getStatIntervalMs()).isEqualTo(2000L);
    }
}