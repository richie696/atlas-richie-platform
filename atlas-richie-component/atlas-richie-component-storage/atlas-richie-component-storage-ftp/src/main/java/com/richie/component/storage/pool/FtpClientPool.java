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
package com.richie.component.storage.pool;

import com.richie.component.storage.bean.FtpConfig;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.io.IOException;
import java.time.Duration;

public class FtpClientPool extends GenericObjectPool<FTPClient> {

    public FtpClientPool(FtpConfig config) {
        super(new FtpClientFactory(config), buildPoolConfig(config));
    }

    private static GenericObjectPoolConfig<FTPClient> buildPoolConfig(FtpConfig config) {
        var cfg = new GenericObjectPoolConfig<FTPClient>();
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

    private static class FtpClientFactory extends BasePooledObjectFactory<FTPClient> {

        private final FtpConfig config;

        FtpClientFactory(FtpConfig config) {
            this.config = config;
        }

        @Override
        public FTPClient create() throws Exception {
            var client = new FTPClient();

            if (config.isPassiveMode()) {
                client.enterLocalPassiveMode();
            } else {
                client.enterLocalActiveMode();
            }
            client.setControlEncoding(config.getCharset().name());
            client.setDataTimeout(config.getDataTimeout());
            client.setConnectTimeout((int) config.getConnectTimeout().toMillis());

            client.connect(config.getHost(), config.getPort());
            int reply = client.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                client.disconnect();
                throw new IOException("FTP server refused connection, reply code: " + reply);
            }

            boolean loggedIn = config.getLoginType() == FtpConfig.LoginType.ANONYMOUS
                    ? client.login("anonymous", "")
                    : client.login(config.getUsername(), config.getPassword());
            if (!loggedIn) {
                client.disconnect();
                throw new IOException("FTP login failed for user: " + config.getUsername());
            }

            client.setFileType(FTP.BINARY_FILE_TYPE);

            if (!client.changeWorkingDirectory(config.getBasePath())) {
                client.makeDirectory(config.getBasePath());
                client.changeWorkingDirectory(config.getBasePath());
            }

            return client;
        }

        @Override
        public PooledObject<FTPClient> wrap(FTPClient client) {
            return new DefaultPooledObject<>(client);
        }

        @Override
        public boolean validateObject(PooledObject<FTPClient> p) {
            try {
                return p.getObject().sendNoOp();
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        public void destroyObject(PooledObject<FTPClient> p) {
            var client = p.getObject();
            try { client.logout(); } catch (IOException ignored) { }
            try { client.disconnect(); } catch (IOException ignored) { }
        }
    }

}
