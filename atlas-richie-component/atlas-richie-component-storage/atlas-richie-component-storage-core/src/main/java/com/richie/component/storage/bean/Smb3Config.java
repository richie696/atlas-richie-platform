package com.richie.component.storage.bean;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * SMB 3.0存储配置
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-04 14:42:49
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "platform.component.storage.smb3")
public class Smb3Config {

    /**
     * 是否启用SMB3存储（true：启用，false：禁用[默认]）
     */
    private Boolean enable = false;

    /**
     * 主机（IP或域名）
     */
    private String domain;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * Base路径（默认：/storage/）
     */
    private String basePath = "/storage/";

    /**
     * 是否启用DFS（默认：false）
     */
    private boolean dfs = false;

    /**
     * DFS TTL（默认：300）
     */
    private Integer dfsTtl = 300;

    /**
     * 是否启用严格视图（默认：false）
     */
    private boolean strictView = false;

    /**
     * 是否启用SMB3签名（默认：false）
     */
    private boolean convertToFQDN = false;

}
