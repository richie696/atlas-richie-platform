package com.richie.component.vector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "platform.component.vector.qdrant")
public class QdRantConfig {

    /**
     * 服务地址
     */
    private String host;

    private Integer port = 6333;

    private boolean useTransportLayerSecurity = false;

    /**
     * 集合名称
     */
    private String collection = "documents";

    private boolean initializeSchema = false;
}
