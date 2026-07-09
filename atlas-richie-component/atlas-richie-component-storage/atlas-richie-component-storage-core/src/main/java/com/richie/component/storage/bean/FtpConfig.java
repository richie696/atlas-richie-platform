/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.storage.bean;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * FTP 连接配置。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "platform.component.storage.ftp")
public class FtpConfig {

    private Boolean enable = false;

    private FtpType ftpType = FtpType.FTP;

    private String host = "localhost";

    private int port = 21;

    private LoginType loginType = LoginType.ANONYMOUS;

    private String username;

    private String password;

    private String basePath = "/";

    private Charset charset = StandardCharsets.UTF_8;

    private String serverLanguageCode = "zh";

    private SystemKey systemKey;

    private boolean passiveMode = true;

    /**
     * 连接池最大连接数
     */
    private int maxTotal = 8;
    /**
     * 连接池最大空闲数
     */
    private int maxIdle = 4;
    /**
     * 连接池最小空闲数
     */
    private int minIdle = 1;
    /**
     * 借出时是否校验连接可用性
     */
    private boolean testOnBorrow = true;
    /**
     * 空闲时是否校验连接可用性
     */
    private boolean testWhileIdle = true;

    /** 连接超时 */
    private Duration connectTimeout = Duration.ofSeconds(15);
    /** 数据传输超时 */
    private Duration dataTimeout = Duration.ofSeconds(30);

    public enum LoginType {
        NORMAL,
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
