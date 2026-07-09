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
package com.richie.component.mqtt.generator.impl;

import com.richie.component.mqtt.config.MqttClientProperties;
import com.richie.component.mqtt.generator.ClientIdRuler;
import com.richie.component.mqtt.generator.IMqttClientDeviceIdGenerator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 默认MQTT客户端设备ID生成器
 * <p>
 * 负责生成MQTT客户端的设备ID，用于标识不同的MQTT客户端实例。
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-11 23:38:35
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultMqttClientDeviceIdGenerator implements IMqttClientDeviceIdGenerator {

    private final MqttClientProperties properties;
    private final ApplicationContext applicationContext;

    @Getter
    @Setter
    private String deviceId;

    @Override
    public String generateDeviceId() {
        if (Objects.isNull(this.deviceId)) {
            ClientIdRuler ruler = (ClientIdRuler) applicationContext.getBean(properties.getClientIdRuler());
            this.deviceId = ruler.getClientId();
        }
        if (StringUtils.isNotBlank(properties.getGroupId())) {
            return String.format("%s_%s", properties.getGroupId(), this.deviceId);
        }
        return this.deviceId;
    }
}
