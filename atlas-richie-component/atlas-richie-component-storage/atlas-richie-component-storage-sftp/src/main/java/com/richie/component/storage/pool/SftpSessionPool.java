package com.richie.component.storage.pool;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.richie.component.storage.bean.SftpConfig;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.time.Duration;
import java.util.Properties;

public class SftpSessionPool extends GenericObjectPool<ChannelSftp> {

    public SftpSessionPool(SftpConfig config) {
        super(new SftpSessionFactory(config), buildPoolConfig(config));
    }

    private static GenericObjectPoolConfig<ChannelSftp> buildPoolConfig(SftpConfig config) {
        var cfg = new GenericObjectPoolConfig<ChannelSftp>();
        cfg.setMaxTotal(config.getMaxTotal());
        cfg.setMaxIdle(config.getMaxIdle());
        cfg.setMinIdle(config.getMinIdle());
        cfg.setTestOnBorrow(config.isTestOnBorrow());
        cfg.setTestWhileIdle(config.isTestWhileIdle());
        cfg.setTimeBetweenEvictionRuns(Duration.ofMinutes(1));
        cfg.setMinEvictableIdleDuration(Duration.ofMinutes(5));
        cfg.setBlockWhenExhausted(true);
        cfg.setMaxWait(Duration.ofSeconds(10));
        return cfg;
    }

    private static class SftpSessionFactory extends BasePooledObjectFactory<ChannelSftp> {

        private final SftpConfig config;

        SftpSessionFactory(SftpConfig config) {
            this.config = config;
        }

        @Override
        public ChannelSftp create() throws Exception {
            var jsch = new JSch();
            if (config.isSshLogin()) {
                jsch.addIdentity(config.getIdentityFile());
            }
            var session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            if (!config.isSshLogin()) {
                session.setPassword(config.getPassword());
            }
            var props = new Properties();
            props.put("StrictHostKeyChecking", "no");
            session.setConfig(props);
            session.connect();

            var channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            return channel;
        }

        @Override
        public PooledObject<ChannelSftp> wrap(ChannelSftp channel) {
            return new DefaultPooledObject<>(channel);
        }

        @Override
        public boolean validateObject(PooledObject<ChannelSftp> p) {
            try {
                p.getObject().pwd();
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void destroyObject(PooledObject<ChannelSftp> p) {
            var channel = p.getObject();
            channel.disconnect();
            try {
                channel.getSession().disconnect();
            } catch (JSchException ignored) {
            }
        }
    }

}
