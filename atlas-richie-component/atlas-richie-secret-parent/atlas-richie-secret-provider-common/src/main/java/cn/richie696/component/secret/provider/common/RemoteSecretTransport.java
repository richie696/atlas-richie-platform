package cn.richie696.component.secret.provider.common;

import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;

import java.time.Instant;
import java.util.Map;

public interface RemoteSecretTransport extends AutoCloseable {
    RemoteValue read(String path, SecretVersionSelector selector);
    byte[] wrap(String key, byte[] plaintext, CryptoContext context);
    byte[] unwrap(String key, byte[] wrapped, CryptoContext context);
    @Override default void close() { }

    record RemoteValue(Object value, String version, Instant createdAt, Map<String, String> attributes) {
        public RemoteValue {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }
}
