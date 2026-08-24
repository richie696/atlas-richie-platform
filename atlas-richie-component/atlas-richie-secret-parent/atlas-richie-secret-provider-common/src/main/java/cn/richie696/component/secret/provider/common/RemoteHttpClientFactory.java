package cn.richie696.component.secret.provider.common;

import java.io.InputStream;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Arrays;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public final class RemoteHttpClientFactory {
    private RemoteHttpClientFactory() { }

    public static HttpClient create(Duration connectTimeout, RemoteProviderProperties.Tls tls,
                             RemoteProviderProperties.Proxy proxy) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout);
        try {
            if (tls != null && ((tls.getTrustStore() != null && !tls.getTrustStore().isBlank())
                    || (tls.getKeyStore() != null && !tls.getKeyStore().isBlank()))) {
                builder.sslContext(sslContext(tls));
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("Provider TLS material cannot be loaded", exception);
        }
        if (proxy != null && proxy.getHost() != null && !proxy.getHost().isBlank()) {
            if (proxy.getPort() < 1 || proxy.getPort() > 65535) {
                throw new IllegalArgumentException("Provider proxy port must be between 1 and 65535");
            }
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost(), proxy.getPort())));
            if (proxy.getUsername() != null && !proxy.getUsername().isBlank()) {
                char[] password = proxy.getPassword();
                builder.authenticator(new Authenticator() {
                    @Override protected PasswordAuthentication getPasswordAuthentication() {
                        if (getRequestorType() != RequestorType.PROXY) return null;
                        return new PasswordAuthentication(proxy.getUsername(), password);
                    }
                });
            }
        }
        return builder.build();
    }

    private static SSLContext sslContext(RemoteProviderProperties.Tls tls) throws Exception {
        TrustManagerFactory trust = null;
        KeyManagerFactory keys = null;
        if (tls.getTrustStore() != null && !tls.getTrustStore().isBlank()) {
            KeyStore store = KeyStore.getInstance(tls.getKeyStoreType());
            char[] password = tls.getTrustStorePassword();
            try (InputStream input = Files.newInputStream(Path.of(tls.getTrustStore()))) { store.load(input, password); }
            finally { Arrays.fill(password, '\0'); }
            trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(store);
        }
        if (tls.getKeyStore() != null && !tls.getKeyStore().isBlank()) {
            KeyStore store = KeyStore.getInstance(tls.getKeyStoreType());
            char[] password = tls.getKeyStorePassword();
            try (InputStream input = Files.newInputStream(Path.of(tls.getKeyStore()))) { store.load(input, password); }
            catch (Exception exception) { Arrays.fill(password, '\0'); throw exception; }
            keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(store, password);
            Arrays.fill(password, '\0');
        }
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys == null ? null : keys.getKeyManagers(), trust == null ? null : trust.getTrustManagers(), null);
        return context;
    }
}
