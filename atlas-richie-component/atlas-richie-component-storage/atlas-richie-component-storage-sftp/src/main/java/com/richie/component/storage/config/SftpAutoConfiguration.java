package com.richie.component.storage.config;

import com.richie.component.storage.bean.SftpConfig;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.Properties;
import java.util.Vector;

/**
 * 文件存储自动配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-05 10:39:15
 */
@Slf4j
@Configuration
@ComponentScan("com.richie.component.storage")
@EnableConfigurationProperties({StorageProperties.class})
public class SftpAutoConfiguration {

    /**
     * SFTP 通道
     *
     * @param properties 存储配置
     * @return 返回SFTP通道
     * @throws JSchException 如果用户名或主机地址无效则抛出该异常
     * @throws SftpException 如果指定的basePath无效则抛出该异常
     */
    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    @ConditionalOnProperty(prefix = "platform.component.storage.sftp", name = "enable", havingValue = "true")
    public ChannelSftp sftp(StorageProperties properties) throws JSchException, SftpException {
        var config = properties.getSftp();
        var channel = getChannelSftp(config);
        channel.connect();
        Vector<?> dirs = channel.ls(config.getBasePath());
        var hasPath = dirs.stream().anyMatch(dir -> dir.toString().equals(config.getBasePath()));
        if (!hasPath) {
            channel.mkdir(config.getBasePath());
        }
        channel.cd(config.getBasePath());
        return channel;
    }

    /**
     * 根据 SFTP 配置创建并连接 ChannelSftp（支持密码或密钥认证）。
     *
     * @param config SFTP 配置
     * @return 已连接 SSH 会话的 SFTP 通道（未 cd 到 basePath）
     * @throws JSchException SSH 连接或认证失败时抛出
     */
    private static ChannelSftp getChannelSftp(SftpConfig config) throws JSchException {
        var jsch = new JSch();
        if (config.isSshLogin()) {
            jsch.addIdentity(config.getIdentityFile());
        }
        var sshSession = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
        if (!config.isSshLogin()) {
            sshSession.setPassword(config.getPassword());
        }
        var prop = new Properties();
        prop.put("StrictHostKeyChecking", "no");
        sshSession.setConfig(prop);
        sshSession.connect();
        return (ChannelSftp) sshSession.openChannel("sftp");
    }

}
