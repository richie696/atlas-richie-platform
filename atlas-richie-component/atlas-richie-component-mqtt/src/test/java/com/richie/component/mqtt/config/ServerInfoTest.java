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
