package com.richie.component.messaging.filter.handler.impl;

import com.richie.context.common.api.SpringContextHolder;
import com.richie.component.messaging.config.MessagingProperties;
import com.richie.component.messaging.event.MessageEvent;
import com.richie.component.messaging.filter.datasource.DatasourceHandler;
import com.richie.component.messaging.filter.handler.MessageHandlerService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

/**
 * 消息处理器接口实现类
 * <p>
 *     本接口作用包括但不限于给消息队列收发的消息进行加工处
 *     理、幂等去重、校验判断等所有和消息处理有关的逻辑。
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-16 17:42:58
 */
@Service
@RequiredArgsConstructor
public class MessageHandlerServiceImpl implements MessageHandlerService {

    /** 消息组件配置，用于选择幂等去重数据源 */
    private final MessagingProperties properties;

    /**
     * 保存消息数据的方法（原子操作，用于幂等去重）
     *
     * @param message 带保存的消息
     * @param expired 该消息的过期时间（单位：毫秒）
     * @return 返回保存结果（true：成功保存，消息首次处理；false：消息已存在，重复消息）
     */
    @Override
    public boolean saveCache(Message<MessageEvent> message, long expired) {
        return getDatasourceHandler().saveCache(message, expired);
    }

    @Override
    public void clearCache(Message<MessageEvent> message) {
        getDatasourceHandler().clearCache(message);
    }

    /**
     * 根据配置的数据源类型获取对应的 DatasourceHandler Bean。
     *
     * @return 内存或 Redis 实现的 DatasourceHandler
     */
    private DatasourceHandler getDatasourceHandler() {
        return SpringContextHolder.getBean(properties.getDatasource().getServiceName());
    }

}
