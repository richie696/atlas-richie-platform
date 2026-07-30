package cn.richie696.antivirus.service;

import cn.richie696.antivirus.config.AntivirusProperties;
import cn.richie696.antivirus.model.ScanRequestedEvent;
import cn.richie696.component.redis.streammq.StreamMQ;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 扫描事件发布器，供 HTTP 受理和宕机恢复共同使用。 */
@Component
@RequiredArgsConstructor
public class ScanEventPublisher {
    private final AntivirusProperties properties;

    public void publish(String taskId) {
        ScanRequestedEvent event = new ScanRequestedEvent();
        event.setTaskId(taskId);
        StreamMQ.stream().publish(properties.getTaskStream(), event);
    }
}
