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
package com.richie.component.mqtt.config;

import com.richie.component.mqtt.enums.NetworkTypeEnum;
import com.richie.component.mqtt.enums.ServerTypeEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServerInfoTest {

    @Test
    void getServerUri_usesPublicHostByDefault() {
        ServerInfo info = new ServerInfo();
        info.setHost("broker.local");
        info.setPort(1883);

        assertThat(info.getServerUri(NetworkTypeEnum.PUBLIC)).isEqualTo("tcp://broker.local:1883");
    }

    @Test
    void getUsername_aliyunFormat() {
        ServerInfo info = new ServerInfo();
        info.setHost("inst001.iot-as-mqtt.cn-shanghai.aliyuncs.com");
        info.setUsername("device");

        assertThat(info.getUsername(ServerTypeEnum.ALIYUN))
                .isEqualTo("Signature|device|inst001");
    }
}
