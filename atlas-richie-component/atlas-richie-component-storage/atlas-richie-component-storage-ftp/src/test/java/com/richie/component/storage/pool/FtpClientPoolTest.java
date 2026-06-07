package com.richie.component.storage.pool;

import com.richie.component.storage.bean.FtpConfig;
import org.apache.commons.net.ftp.FTPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Disabled("""
        需要本地 FTP 服务器(127.0.0.1:21, 凭据 ftpuser/ftppass)，
        但测试中无 @BeforeAll 启动服务器。本地构建/CI 环境无 FTP 服务。
        后续可改用 MockFtpServer 或 Testcontainers 重新实现。
        """)
class FtpClientPoolTest {

    private static final String FTP_HOST = "127.0.0.1";
    private static final int FTP_PORT = 21;
    private static final String FTP_USER = "ftpuser";
    private static final String FTP_PASS = "ftppass";

    private FtpClientPool pool;

    @AfterEach
    void tearDown() {
        if (pool != null) {
            pool.close();
        }
    }

    @Test
    void pool_canBeConstructedWithConfig() {
        var config = createConfig("ftp.example.com", 21, "user", "secret", "/uploads");
        config.setMaxTotal(4);
        config.setMaxIdle(2);
        config.setMinIdle(1);
        config.setConnectTimeout(Duration.ofSeconds(5));
        config.setDataTimeout(Duration.ofSeconds(10));

        pool = new FtpClientPool(config);
        assertThat(pool).isNotNull();
        assertThat(pool.getMaxTotal()).isEqualTo(4);
        assertThat(pool.getMaxIdle()).isEqualTo(2);
        assertThat(pool.getMinIdle()).isEqualTo(1);
    }

    @Test
    void pool_anonymousLoginConfig() {
        var config = createConfig("ftp.example.com", 21, null, null, "/");
        config.setLoginType(FtpConfig.LoginType.ANONYMOUS);

        pool = new FtpClientPool(config);
        assertThat(pool).isNotNull();
    }

    @Test
    void borrowAndReturn_client() throws Exception {
        pool = createLocalPool();

        FTPClient client = pool.borrowObject();
        assertThat(client).isNotNull();
        assertThat(client.isConnected()).isTrue();
        assertThat(client.isAvailable()).isTrue();

        pool.returnObject(client);
        assertThat(pool.getNumActive()).isZero();
        assertThat(pool.getNumIdle()).isEqualTo(1);
    }

    @Test
    void borrowMultipleClients() throws Exception {
        pool = createLocalPool();
        pool.setMaxTotal(3);

        FTPClient c1 = pool.borrowObject();
        FTPClient c2 = pool.borrowObject();
        FTPClient c3 = pool.borrowObject();

        assertThat(c1).isNotNull();
        assertThat(c2).isNotNull();
        assertThat(c3).isNotNull();
        assertThat(pool.getNumActive()).isEqualTo(3);

        pool.returnObject(c1);
        pool.returnObject(c2);
        pool.returnObject(c3);

        assertThat(pool.getNumActive()).isZero();
    }

    @Test
    void borrowAndReturn_inMultipleCycles() throws Exception {
        pool = createLocalPool();
        pool.setMaxTotal(2);
        pool.setMaxIdle(2);

        FTPClient c1 = pool.borrowObject();
        FTPClient c2 = pool.borrowObject();
        pool.returnObject(c1);
        pool.returnObject(c2);

        FTPClient c1b = pool.borrowObject();
        FTPClient c2b = pool.borrowObject();

        assertThat(c1b).isNotNull();
        assertThat(c2b).isNotNull();
        assertThat(c1b.isConnected()).isTrue();

        pool.returnObject(c1b);
        pool.returnObject(c2b);
    }

    @Test
    void closePool_destroysIdleClients() throws Exception {
        pool = createLocalPool();
        pool.setMinIdle(0);

        FTPClient client = pool.borrowObject();
        pool.returnObject(client);

        assertThat(pool.getNumIdle()).isEqualTo(1);
        pool.close();
        assertThat(pool.isClosed()).isTrue();
    }

    @Test
    void close_closesAllConnections() throws Exception {
        pool = createLocalPool();

        FTPClient c1 = pool.borrowObject();
        FTPClient c2 = pool.borrowObject();
        pool.returnObject(c1);
        pool.returnObject(c2);

        pool.close();
        assertThat(pool.isClosed()).isTrue();
        assertThat(c1.isConnected()).isFalse();
        assertThat(c2.isConnected()).isFalse();
    }

    @Test
    void borrowAfterClose_throwsException() throws Exception {
        pool = createLocalPool();
        pool.close();

        assertThatThrownBy(() -> pool.borrowObject())
                .isInstanceOf(Exception.class);
    }

    @Test
    void borrowFromInvalidHost_throwsException() {
        var config = createConfig("192.0.2.1", 21, FTP_USER, FTP_PASS, "/");
        config.setConnectTimeout(Duration.ofSeconds(1));
        pool = new FtpClientPool(config);

        assertThatThrownBy(() -> pool.borrowObject())
                .isInstanceOf(Exception.class);
    }

    @Test
    void borrowWithWrongCredentials_throwsException() {
        var config = createConfig(FTP_HOST, FTP_PORT, "wrong", "wrong", "/");
        config.setConnectTimeout(Duration.ofSeconds(3));
        pool = new FtpClientPool(config);

        assertThatThrownBy(() -> pool.borrowObject())
                .isInstanceOf(Exception.class);
    }

    @Test
    void poolMetrics_updatedAfterBorrowReturn() throws Exception {
        pool = createLocalPool();

        assertThat(pool.getCreatedCount()).isZero();
        assertThat(pool.getNumActive()).isZero();

        FTPClient client = pool.borrowObject();
        assertThat(pool.getCreatedCount()).isEqualTo(1);
        assertThat(pool.getNumActive()).isEqualTo(1);

        pool.returnObject(client);
        assertThat(pool.getNumActive()).isZero();
        assertThat(pool.getNumIdle()).isEqualTo(1);
    }

    @Test
    void borrow_withActiveMode() throws Exception {
        var config = createConfig(FTP_HOST, FTP_PORT, FTP_USER, FTP_PASS, "/");
        config.setPassiveMode(false);
        pool = new FtpClientPool(config);

        FTPClient client = pool.borrowObject();
        assertThat(client.isConnected()).isTrue();
        pool.returnObject(client);
    }

    @Test
    void borrowFromNonFtpPort_throwsException() throws Exception {
        try (var serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            var thread = new Thread(() -> {
                try (var s = serverSocket.accept()) {
                    var pw = new java.io.PrintWriter(s.getOutputStream(), true);
                    pw.println("500 I'm not an FTP server");
                    Thread.sleep(3000);
                } catch (Exception ignored) {
                }
            });
            thread.setDaemon(true);
            thread.start();
            Thread.sleep(500);

            var config = createConfig("127.0.0.1", port, FTP_USER, FTP_PASS, "/");
            config.setConnectTimeout(Duration.ofSeconds(5));
            pool = new FtpClientPool(config);

            assertThatThrownBy(() -> pool.borrowObject())
                    .isInstanceOf(Exception.class);
        }
    }

    @Test
    void borrow_withNewBasePath() throws Exception {
        var config = createConfig(FTP_HOST, FTP_PORT, FTP_USER, FTP_PASS, "/test-ftp-jacoco-dir");
        pool = new FtpClientPool(config);

        FTPClient client = pool.borrowObject();
        assertThat(client.isConnected()).isTrue();
        pool.returnObject(client);
    }

    @Test
    void destroyObject_alreadyDisconnectedClient() throws Exception {
        pool = createLocalPool();
        pool.setMinIdle(0);

        FTPClient client = pool.borrowObject();
        client.disconnect();
        pool.returnObject(client);
        pool.close();

        assertThat(pool.isClosed()).isTrue();
    }

    private static FtpConfig createConfig(String host, int port, String user, String pass, String basePath) {
        var config = new FtpConfig();
        config.setHost(host);
        config.setPort(port);
        config.setUsername(user);
        config.setPassword(pass);
        config.setBasePath(basePath);
        config.setLoginType(FtpConfig.LoginType.NORMAL);
        config.setPassiveMode(true);
        config.setConnectTimeout(Duration.ofSeconds(5));
        config.setDataTimeout(Duration.ofSeconds(10));
        config.setMaxTotal(4);
        config.setMaxIdle(2);
        config.setMinIdle(0);
        config.setTestOnBorrow(true);
        config.setTestWhileIdle(true);
        return config;
    }

    private static FtpClientPool createLocalPool() {
        var config = createConfig(FTP_HOST, FTP_PORT, FTP_USER, FTP_PASS, "/");
        return new FtpClientPool(config);
    }
}
