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
package com.richie.component.logging.config;

import com.richie.component.logging.enums.RecordTypeEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperateLogPropertiesTest {

    @Test
    void defaults_matchSpecification() {
        OperateLogProperties properties = new OperateLogProperties();

        assertThat(properties.isEnable()).isTrue();
        assertThat(properties.getRecordType()).isEqualTo(RecordTypeEnum.FILE);
        assertThat(properties.getMqTopicName()).isEqualTo("access-log-out-0");
        assertThat(properties.getCacheAccessLogKey()).isEqualTo("platform:access-log");
        assertThat(properties.getDbBatchSize()).isEqualTo(500);
    }

    @Test
    void setters_updateValues() {
        OperateLogProperties properties = new OperateLogProperties();
        properties.setEnable(false);
        properties.setRecordType(com.richie.component.logging.enums.RecordTypeEnum.REDIS);
        properties.setMqTopicName("custom-topic");
        properties.setCacheAccessLogKey("custom:key");

        assertThat(properties.isEnable()).isFalse();
        assertThat(properties.getRecordType()).isEqualTo(com.richie.component.logging.enums.RecordTypeEnum.REDIS);
        assertThat(properties.getMqTopicName()).isEqualTo("custom-topic");
        assertThat(properties.getCacheAccessLogKey()).isEqualTo("custom:key");
    }
}
