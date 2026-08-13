package cn.richie696.component.mcp.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Properties;

/**
 * 从 classpath 加载并校验固定版本的官方 MCP JSON Schema。
 *
 * 为什么需要这个类：MCP 协议要求客户端/服务端在协商时按官方发布的 JSON Schema 校验
 * 报文结构。如果运行时直接下载/缓存远端 Schema，会面临两个风险：
 * <ol>
 *   <li>Schema 仓库被劫持或下线。</li>
 *   <li>Schema 内容在传递过程中被篡改，导致协议层接受错误报文。</li>
 * </ol>
 * 本类采用"打包期内置 + 加载期强校验"模式：把每个版本对应的 Schema 与 manifest
 * 一起打入 classpath（{@code META-INF/mcp/schema/<version>/}），加载时强制校验
 * SHA-256 摘要与 manifest 中声明的协议版本，不一致即拒绝启动。
 *
 * 关键设计：
 * <ul>
 *   <li>目前仅支持 {@link McpProtocolVersions#V_2026_07_28}，扩展时只需新增
 *       {@code META-INF/mcp/schema/<version>/} 资源并在 {@code load} 中追加白名单。</li>
 *   <li>{@code bytes()} 每次返回防御性拷贝，避免外部持有内部 buffer。</li>
 *   <li>{@code source / sourceCommit} 字段记录了 Schema 来自哪个 Git 仓库的哪个 commit，
 *       便于审计与排障。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpSchemaSnapshot {
    private static final String ROOT = "META-INF/mcp/schema/";
    private static final String SCHEMA_FILE = "/schema.json";
    private static final String MANIFEST_FILE = "/snapshot.properties";

    private final String protocolVersion;
    private final String schemaDialect;
    private final String source;
    private final String sourceCommit;
    private final String sha256;
    private final byte[] bytes;

    private McpSchemaSnapshot(
            String protocolVersion,
            String schemaDialect,
            String source,
            String sourceCommit,
            String sha256,
            byte[] bytes) {
        this.protocolVersion = protocolVersion;
        this.schemaDialect = schemaDialect;
        this.source = source;
        this.sourceCommit = sourceCommit;
        this.sha256 = sha256;
        // 防御性拷贝：避免外部修改影响快照完整性
        this.bytes = bytes.clone();
    }

    /**
     * 加载并校验指定协议版本的 Schema 快照。
     *
     * <p>校验三件事：
     * <ol>
     *   <li>Schema 文件存在且可读。</li>
     *   <li>SHA-256 与 manifest 声明一致（防篡改）。</li>
     *   <li>manifest 中声明的 {@code protocolVersion} 与入参一致（防错位）。</li>
     * </ol>
     *
     * @param protocolVersion 协议版本号，目前仅接受 {@link McpProtocolVersions#V_2026_07_28}
     * @return 通过校验的快照实例
     * @throws IllegalArgumentException 当版本不在白名单时
     * @throws IllegalStateException    当资源缺失、SHA-256 不匹配或 manifest 版本不一致时
     */
    public static McpSchemaSnapshot load(String protocolVersion) {
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        if (!McpProtocolVersions.V_2026_07_28.equals(protocolVersion)) {
            throw new IllegalArgumentException("No MCP schema snapshot for version: " + protocolVersion);
        }
        String base = ROOT + protocolVersion;
        Properties metadata = loadProperties(base + MANIFEST_FILE);
        byte[] schema = loadBytes(base + SCHEMA_FILE);
        String expected = required(metadata, "sha256");
        String actual = sha256(schema);
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    "MCP schema snapshot checksum mismatch: expected=" + expected + ", actual=" + actual);
        }
        String manifestVersion = required(metadata, "protocolVersion");
        if (!protocolVersion.equals(manifestVersion)) {
            throw new IllegalStateException(
                    "MCP schema snapshot version mismatch: expected=" + protocolVersion
                            + ", actual=" + manifestVersion);
        }
        return new McpSchemaSnapshot(
                manifestVersion,
                required(metadata, "schemaDialect"),
                required(metadata, "source"),
                required(metadata, "sourceCommit"),
                expected,
                schema);
    }

    /**
     * 返回 manifest 中声明的协议版本。
     *
     * @return 协议版本字符串
     */
    public String protocolVersion() {
        return protocolVersion;
    }

    /**
     * 返回 Schema 方言标识（用于区分 2025/2026 等不同方言的校验规则）。
     *
     * @return Schema 方言字符串
     */
    public String schemaDialect() {
        return schemaDialect;
    }

    /**
     * 返回 Schema 原始来源（如 GitHub 仓库 URL）。
     *
     * @return 来源字符串
     */
    public String source() {
        return source;
    }

    /**
     * 返回 Schema 对应的 Git commit。
     *
     * @return commit 标识
     */
    public String sourceCommit() {
        return sourceCommit;
    }

    /**
     * 返回 manifest 中声明的 SHA-256 摘要（小写十六进制）。
     *
     * @return 摘要字符串
     */
    public String sha256() {
        return sha256;
    }

    /**
     * 返回 Schema 字节内容的防御性拷贝。
     *
     * @return 字节数组副本，外部修改不影响内部状态
     */
    public byte[] bytes() {
        return bytes.clone();
    }

    private static Properties loadProperties(String resource) {
        Properties result = new Properties();
        try (InputStream input = resource(resource)) {
            result.load(input);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load MCP schema metadata: " + resource, exception);
        }
    }

    private static byte[] loadBytes(String resource) {
        try (InputStream input = resource(resource)) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load MCP schema: " + resource, exception);
        }
    }

    private static InputStream resource(String path) {
        InputStream input = McpSchemaSnapshot.class.getClassLoader().getResourceAsStream(path);
        if (input == null) {
            throw new IllegalStateException("Missing MCP schema resource: " + path);
        }
        return input;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing MCP schema metadata: " + key);
        }
        return value;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide SHA-256", exception);
        }
    }
}
