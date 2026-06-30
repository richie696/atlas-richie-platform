package com.richie.component.storage.config;

import com.richie.component.storage.bean.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件存储服务配置信息
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-05 10:56:09
 */
@Data
@ConfigurationProperties(prefix = "platform.component.storage")
public class StorageProperties {

    /**
     * 本地存储配置（默认：程序所在目录下的./storage目录内）
     */
    private LocalConfig local = new LocalConfig("./storage/");

    /**
     * FTP存储配置
     */
    private FtpConfig ftp = new FtpConfig();

    /**
     * SFTP存储配置
     */
    private SftpConfig sftp = new SftpConfig();

    /**
     * SMB3存储配置
     */
    private Smb3Config smb3 = new Smb3Config();

    /**
     * 对象存储配置
     */
    private ObjectConfig object = new ObjectConfig();

}
