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
        this.bytes = bytes.clone();
    }

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

    public String protocolVersion() {
        return protocolVersion;
    }

    public String schemaDialect() {
        return schemaDialect;
    }

    public String source() {
        return source;
    }

    public String sourceCommit() {
        return sourceCommit;
    }

    public String sha256() {
        return sha256;
    }

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
