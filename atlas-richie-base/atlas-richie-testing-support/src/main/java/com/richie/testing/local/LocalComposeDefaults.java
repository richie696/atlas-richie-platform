package com.richie.testing.local;

/**
 * 本机 docker-compose 默认连接（与 {@code ~/Development/docker-scripts} 下脚本一致）。
 *
 * <p>storage-local 等双中间件集测在端口可达时优先连接已有容器，避免重复拉镜像/起容器。
 */
public final class LocalComposeDefaults {

    /** {@code redis/redis-local/docker-compose.yml} → 16379:6379 */
    public static final String REDIS_HOST = "localhost";
    public static final int REDIS_PORT = 16379;
    public static final String REDIS_PASSWORD = "Redis2025!Local";

    /** {@code mysql/mysql/docker-compose.yml} → 53366:3306 */
    public static final String MYSQL_HOST = "localhost";
    public static final int MYSQL_PORT = 53366;
    public static final String MYSQL_DATABASE = "platform";
    public static final String MYSQL_USERNAME = "richie696";
    public static final String MYSQL_PASSWORD = "wjy123456";

    private static final int PROBE_TIMEOUT_MS = 800;

    private LocalComposeDefaults() {
    }

    public static boolean isRedisReachable() {
        return TcpPortProbe.isOpen(REDIS_HOST, REDIS_PORT, PROBE_TIMEOUT_MS);
    }

    public static boolean isMySqlReachable() {
        return TcpPortProbe.isOpen(MYSQL_HOST, MYSQL_PORT, PROBE_TIMEOUT_MS);
    }

    public static boolean isStorageLocalStackReady() {
        return isRedisReachable() && isMySqlReachable();
    }
}
