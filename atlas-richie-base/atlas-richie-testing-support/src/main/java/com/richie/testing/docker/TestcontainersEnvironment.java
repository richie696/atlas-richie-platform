package com.richie.testing.docker;

import com.richie.testing.env.TestEnv;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Testcontainers Docker 环境探测与 macOS Docker Desktop socket 适配。
 */
public final class TestcontainersEnvironment {

    private static volatile boolean initialized;

    private TestcontainersEnvironment() {
    }

    /** 在首次使用 Testcontainers 前调用（类静态块中）。 */
    public static void ensureConfigured() {
        if (initialized) {
            return;
        }
        synchronized (TestcontainersEnvironment.class) {
            if (initialized) {
                return;
            }
            configureDockerSocketIfNeeded();
            initialized = true;
        }
    }

    private static void configureDockerSocketIfNeeded() {
        if (TestEnv.firstNonBlank(System.getenv("DOCKER_HOST"), System.getProperty("docker.host")) != null) {
            ensureEnvironmentDockerStrategy();
            return;
        }
        if (Files.exists(Path.of("/var/run/docker.sock"))) {
            return;
        }
        Path desktopSocket = Path.of(System.getProperty("user.home"), ".docker/run/docker.sock");
        if (Files.exists(desktopSocket)) {
            String unixHost = "unix://" + desktopSocket;
            System.setProperty("docker.host", unixHost);
            System.setProperty("testcontainers.docker.socket.override", desktopSocket.toString());
            ensureEnvironmentDockerStrategy();
        }
    }

    private static void ensureEnvironmentDockerStrategy() {
        if (TestEnv.firstNonBlank(
                System.getenv("TESTCONTAINERS_DOCKER_CLIENT_STRATEGY"),
                System.getProperty("docker.client.strategy")) != null) {
            return;
        }
        System.setProperty(
                "docker.client.strategy",
                "org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy");
    }
}
