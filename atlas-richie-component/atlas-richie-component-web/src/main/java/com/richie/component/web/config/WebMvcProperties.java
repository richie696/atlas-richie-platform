package com.richie.component.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 平台WebMvc属性配置
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-09 15:42:03
 */
@Data
@ConfigurationProperties(prefix = "platform.component.web")
public class WebMvcProperties {

    /**
     * IETF BCP 47 标准语言标签，例：
     * <p>
     * zh-CN
     * en-US
     * ja-JP
     * ko-KR
     */
    private List<String> supportedLanguageTags;

    /**
     * 登录地址清单
     */
    private Set<String> loginUrls;

    /**
     * 令牌签发秘钥
     */
    private String tokenSecret;

    /**
     * 令牌有效时长
     */
    private long tokenExpirationDate;

    /**
     * 默认 Locale（IETF BCP 47 语言标签，如 zh-CN、en-US）。
     * <p>当 i18n 组件同时存在时，建议两边配置保持一致。
     * 默认值：{@code Locale.CHINA}。
     */
    private Locale defaultLocale = Locale.CHINA;

    /**
     * 跨域配置
     */
    private CorsConfig cors = new CorsConfig();
}
