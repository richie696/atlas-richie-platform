package com.richie.component.mqtt.service.impl;

import com.richie.context.utils.data.JsonUtils;
import com.richie.component.mqtt.beans.ConsumerMessage;
import com.richie.component.mqtt.service.MqttDemoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * MQTT 演示业务服务接口
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-02 10:40:27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttDemoServiceImpl implements MqttDemoService {

    @Override
    public void handleMessage(ConsumerMessage message) {
        log.info(JsonUtils.getInstance().serialize(message, true));
    }
}
