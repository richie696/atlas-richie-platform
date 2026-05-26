package com.richie.component.storage.bean;

import cn.hutool.extra.ftp.FtpMode;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static com.richie.component.storage.bean.FtpConfig.FtpType.FTP;
import static com.richie.component.storage.bean.FtpConfig.LoginType.ANONYMOUS;

/**
 * FTP配置
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-04 13:42:35
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "platform.component.storage.ftp")
public class FtpConfig {

    /**
     * 是否启用FTP存储（true：启用，false：禁用[默认]）
     */
    private Boolean enable = false;

    /**
     * FTP类型
     */
    private FtpType ftpType = FTP;

    /**
     * 主机（IP或域名，默认：localhost）
     */
    private String host = "localhost";

    /**
     * 端口（默认：21）
     */
    private int port = 21;

    /**
     * 登录类型（正常、匿名）
     */
    private LoginType loginType = ANONYMOUS;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * Base路径（默认：/）
     */
    private String basePath = "/";

    /**
     * 编码（默认：UTF-8）
     */
    private Charset charset = StandardCharsets.UTF_8;

    /**
     * 服务语言编码（默认：zh）
     */
    private String serverLanguageCode = "zh";

    /**
     * 系统标识符
     */
    private SystemKey systemKey;

    /**
     * 是否启用被动模式（默认：true）
     */
    private FtpMode ftpMode;


    /**
     * 登录类型枚举
     *
     * @author richie696
     * @version 1.0
     * @since 2023-09-04 13:45:31
     */
    public enum LoginType {
        /**
         * 正常
         */
        NORMAL,
        /**
         * 匿名
         */
        ANONYMOUS
    }

    public enum FtpType {
        /**
         * FTP
         */
        FTP,
        /**
         * FTPs
         */
        FTPS
    }

    @Getter
    @RequiredArgsConstructor
    public enum SystemKey {

        /**
         * 基于 unix 的 ftp 服务器的标识符
         */
        SYST_UNIX("UNIX"),

        /**
         * 备用 UNIX 解析器的标识符;与 {@code SYST_UNIX} 相同，但从文件名中删
         * 除前导空格。这是为了保持与解析器的原始行为的向后兼容性，该行为忽
         * 略了文件名日期和开头之间的多个空格。
         */
        SYST_UNIX_TRIM_LEADING("UNIX_LTRIM"),

        /**
         * 基于 vms 的 ftp 服务器的标识符
         */
        SYST_VMS("VMS"),

        /**
         * 基于WindowsNT的ftp服务器的标识符
         */
        SYST_NT("WINDOWS"),

        /**
         * 基于 OS/2 的 ftp 服务器的标识符
         */
        SYST_OS2("OS/2"),

        /**
         * 基于 OS/400 的 ftp 服务器的标识符
         */
        SYST_OS400("OS/400"),

        /**
         * 基于 AS/400 的 ftp 服务器的标识符
         */
        SYST_AS400("AS/400"),

        /**
         * 基于 MVS 的 ftp 服务器的标识符
         */
        SYST_MVS("MVS"),

        /**
         * 某些服务器返回“未知类型：L8”消息以响应 SYST 命令。我们将
         * 这些设置为Unix类型的系统。如果有问题的 ftpd 是在没有系统
         * 信息的情况下编译的，则可能会发生这种情况。
         */
        SYST_L8("TYPE: L8"),

        /**
         * 基于 Netware 的 ftp 服务器的标识符
         */
        SYST_NETWARE("NETWARE"),

        /**
         * 基于 Mac pre OS-X 的 ftp 服务器的标识符
         *
         * @since 3.1
         */
        SYST_MACOS_PETER("MACOS PETER");

        private final String value;
    }
}
