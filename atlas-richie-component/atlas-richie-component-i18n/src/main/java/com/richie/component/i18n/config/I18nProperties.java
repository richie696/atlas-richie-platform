package com.richie.component.i18n.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

/**
 * 国际化配置文件
 *
 * @author richie696
 * @version 1.0
 * @since 2021-10-01 18:21:39
 */
@Data
@ConfigurationProperties(prefix = "platform.component.i18n")
public class I18nProperties {

    /**
     * 默认构造函数（供配置绑定使用）。
     */
    public I18nProperties() {
    }

    /**
     * 国际化资源存放路径
     */
    private String path = "i18n/messages";

    /**
     * 国际化资源文件编码
     */
    private String encoding = "UTF-8";

    /**
     * 国际化资源文件默认区域（默认：中国）
     */
    private Locale defaultLocale = Locale.CHINA;

    /**
     * 是否对控制器进行国际化处理（默认：false，推荐：true）
     * <p style="color: red">☆☆☆ 重要：可提升国际化切面执行的性能，但是需要在所有需要国际化的
     * controller 上增加 {@snippet com.richie.component.i18n.annotation.I18nControl} 注解，否
     * 则切面国际化功能将不会生效。
     *
     */
    private Boolean enableI18nControl = false;

}
