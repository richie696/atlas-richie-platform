package com.richie.component.storage.pool;

import com.richie.component.storage.bean.SftpConfig;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class SftpSessionPool extends GenericObjectPool<ClientSession> {

    public SftpSessionPool(SshClient sshClient, SftpConfig config) {
        super(new SftpSessionFactory(sshClient, config), buildPoolConfig(config));
    }

    private static GenericObjectPoolConfig<ClientSession> buildPoolConfig(SftpConfig config) {
        var cfg = new GenericObjectPoolConfig<ClientSession>();
        cfg.setMaxTotal(config.getMaxTotal());
        cfg.setMaxIdle(config.getMaxIdle());
        cfg.setMinIdle(config.getMinIdle());
        cfg.setTestOnBorrow(config.isTestOnBorrow());
        cfg.setTestWhileIdle(config.isTestWhileIdle());
        cfg.setTestOnReturn(true);
        cfg.setTimeBetweenEvictionRuns(Duration.ofMinutes(1));
        cfg.setMinEvictableIdleDuration(Duration.ofMinutes(5));
        cfg.setBlockWhenExhausted(true);
        cfg.setMaxWait(Duration.ofSeconds(10));
        return cfg;
    }

    private static class SftpSessionFactory extends BasePooledObjectFactory<ClientSession> {

        private final SshClient sshClient;
        private final SftpConfig config;

        SftpSessionFactory(SshClient sshClient, SftpConfig config) {
            this.sshClient = sshClient;
            this.config = config;
        }

        @Override
        public ClientSession create() throws Exception {
            var session = sshClient
                    .connect(config.getUsername(), config.getHost(), config.getPort())
                    .verify(15, TimeUnit.SECONDS)
                    .getSession();

            if (config.isSshLogin()) {
                var keyPath = Paths.get(config.getIdentityFile());
                var keyProvider = new FileKeyPairProvider(keyPath);
                for (var kp : keyProvider.loadKeys(session)) {
                    session.addPublicKeyIdentity(kp);
                }
            } else {
                session.addPasswordIdentity(config.getPassword());
            }

            session.auth().verify(10, TimeUnit.SECONDS);
            return session;
        }

        @Override
        public PooledObject<ClientSession> wrap(ClientSession session) {
            return new DefaultPooledObject<>(session);
        }

        @Override
        public boolean validateObject(PooledObject<ClientSession> p) {
            return p.getObject().isOpen();
        }

        @Override
        public void destroyObject(PooledObject<ClientSession> p) {
            try {
                p.getObject().close();
            } catch (IOException ignored) {
            }
        }
    }

}
