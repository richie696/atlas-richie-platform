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
