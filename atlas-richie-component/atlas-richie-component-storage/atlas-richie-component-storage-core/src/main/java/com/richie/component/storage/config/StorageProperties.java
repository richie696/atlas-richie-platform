package com.richie.component.storage.config;

import com.richie.component.storage.bean.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件存储服务配置信息
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-05 10:56:09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "platform.component.storage")
public class StorageProperties {

    /**
     * 是否自动初始化存储引擎（默认：true）
     * <p>
     * true  = 自动模式：根据 YAML 配置自动创建引擎 Bean
     * false = 手动模式：不创建任何引擎 Bean，需通过 Registry 手工创建
     */
    @Builder.Default
    private Boolean autoInit = true;

    /**
     * 本地存储配置（默认：程序所在目录下的./storage目录内）
     */
    @Builder.Default
    private LocalConfig local = new LocalConfig("./storage/");

    /**
     * FTP存储配置
     */
    @Builder.Default
    private FtpConfig ftp = new FtpConfig();

    /**
     * SFTP存储配置
     */
    @Builder.Default
    private SftpConfig sftp = new SftpConfig();

    /**
     * SMB3存储配置
     */
    @Builder.Default
    private Smb3Config smb3 = new Smb3Config();

    /**
     * 对象存储配置
     */
    @Builder.Default
    private ObjectConfig object = new ObjectConfig();

}
