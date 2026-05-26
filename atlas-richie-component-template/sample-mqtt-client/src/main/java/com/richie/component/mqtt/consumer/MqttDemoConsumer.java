package com.richie.component.mqtt.consumer;

import com.richie.component.mqtt.beans.ConsumerMessage;
import com.richie.component.mqtt.client.MqttClientApi;
import com.richie.component.mqtt.service.MqttDemoService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * MQTT消费者类
 * <p style="color: red">要想使用消费者，需要引入mqtt-client的配置文件，执行Client初始化， 推荐使用2个IDEA进行模拟，一个引入mqtt-server，另一个引入mqtt-client。
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-02 10:31:59
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttDemoConsumer implements Consumer<ConsumerMessage> {

    private final MqttClientApi mqttClient;

    private final MqttDemoService mqttDemoService;

    @PostConstruct
    public void initial() {
        // 需要绑定监听器的 topic
        String topic = "%s/9996/object".formatted(mqttClient.getParentTopic());
        // 执行消费者注册（即：将 topic 绑定回调事件）
        mqttClient.registerConsumer(topic, this);
        // 第二种注册方式
        innerRegister();
    }

    @Override
    public void accept(ConsumerMessage message) {
        // 收到消息时执行该回调进行处理
        mqttDemoService.handleMessage(message);
    }


    private void innerRegister() {
        // 需要绑定监听器的 topic
        String topic = "%s/9996/00FFBE87C719".formatted(mqttClient.getParentTopic());
        // 执行消费者注册（即：将 topic 绑定回调事件）
        mqttClient.registerConsumer(topic, message -> {
            log.info("\n\n\n\n=========【MQTT_Consumer】===========");
            log.info("读取到消息：{}\n\n\n\n", message.toString());
        });
    }

    public void destroy() {
        // 取消注册消费者
        mqttClient.unregisterConsumers(
                "%s/9996/00FFBE87C719".formatted(mqttClient.getParentTopic()),
                "%s/9996/object".formatted(mqttClient.getParentTopic())
        );
    }

}
