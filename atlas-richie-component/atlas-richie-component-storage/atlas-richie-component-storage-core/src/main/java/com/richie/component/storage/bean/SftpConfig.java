package com.richie.component.storage.bean;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

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
@ConfigurationProperties(prefix = "platform.component.storage.sftp")
public class SftpConfig {

    /**
     * 是否使用SSH登录（true：使用，false：不使用[默认，密码登录]）
     */
    private boolean sshLogin = false;

    /**
     * 是否启用SFTP存储（true：启用，false：禁用[默认]）
     */
    private Boolean enable = false;

    /**
     * 主机（IP或域名，默认：localhost）
     */
    private String host = "localhost";

    /**
     * 端口（默认：21）
     */
    private int port = 21;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 密钥文件路径
     */
    private String identityFile;

    /**
     * Base路径（默认：/）
     */
    private String basePath = "/";

}
