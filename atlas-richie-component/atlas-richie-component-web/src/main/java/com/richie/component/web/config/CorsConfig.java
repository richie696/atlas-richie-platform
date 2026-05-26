package com.richie.component.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 跨域配置属性（前缀：platform.component.web.cors）。
 *
 * @author richie696
 * @since 2022-10-09
 */
@Data
@ConfigurationProperties(prefix = "platform.component.web.cors")
public class CorsConfig {

    /** 是否启用 CORS */
    private boolean enable = false;

    /** 映射路径模式，默认 /** */
    private String pathPattern = "/**";

    /** 允许的源（与 allowedOriginPatterns 二选一） */
    private String[] allowedOrigin;

    /** 允许的源模式（支持通配），默认 * */
    private String[] allowedOriginPatterns = new String[] {"*"};

    /** 允许的 HTTP 方法 */
    private String[] allowedMethods = new String[] {"GET", "POST", "PUT", "DELETE", "OPTIONS"};

    /** 允许的请求头 */
    private String[] allowedHeaders = new String[] {"*"};

    /** 暴露给前端的响应头 */
    private String[] exposedHeaders;

    /** 是否允许携带凭证 */
    private Boolean allowCredentials = true;

    /**
     * 是否允许专用网络访问（如从公网到 router.local、到 localhost 的请求）。
     */
    private Boolean allowPrivateNetwork = false;

    /** 预检请求缓存时间（秒） */
    private long maxAge = 3600;
}
