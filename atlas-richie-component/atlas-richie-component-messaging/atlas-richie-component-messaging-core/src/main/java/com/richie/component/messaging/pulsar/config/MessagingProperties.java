package com.richie.component.messaging.pulsar.config;

import com.richie.component.messaging.enums.DatasourceTypeEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring Cloud Stream 消息组件配置属性。
 * <p>
 * 配置前缀：{@code spring.cloud.stream}，用于幂等去重数据源与重试次数等。
 *
 * @author richie696
 * @since 2022-09-16
 */
@Data
@ConfigurationProperties(prefix = "spring.cloud.stream")
public class MessagingProperties {

    /**
     * 默认构造函数（供配置绑定使用）。
     */
    public MessagingProperties() {
    }

    /**
     * 幂等去重使用的数据缓存源（可选值：memory、redis）
     */
    private DatasourceTypeEnum datasource = DatasourceTypeEnum.MEMORY;

    /**
     * 消息处理失败后的最大重试次数（默认：3次）
     */
    private Integer maxRetries = 3;

}
