package cn.richie696.component.secret.provider.kmip;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmipTransportPolicyTest {

    @Test
    void appliesConfiguredConnectAndReadTimeoutsBeforeHandshake() throws Exception {
        RecordingSslSocket socket = new RecordingSslSocket();
        SSLSocketFactory factory = new RecordingSslSocketFactory(socket);

        assertThat(KmipSecretClient.connectSocket(
                factory,
                URI.create("kmips://kmip.example:5697"),
                Duration.ofMillis(321),
                Duration.ofMillis(654))).isSameAs(socket);

        assertThat(socket.remoteAddress).isEqualTo(new InetSocketAddress("kmip.example", 5697));
        assertThat(socket.connectTimeout).isEqualTo(321);
        assertThat(socket.readTimeout).isEqualTo(654);
    }

    @Test
    void retriesOnlyTransientIoFailuresWithinTheConfiguredBudget() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        String value = KmipSecretClient.retryIo(3, () -> {
            if (attempts.incrementAndGet() < 3) throw new IOException("temporary");
            return "ok";
        });

        assertThat(value).isEqualTo("ok");
        assertThat(attempts).hasValue(3);

        AtomicInteger handshakeAttempts = new AtomicInteger();
        assertThatThrownBy(() -> KmipSecretClient.retryIo(3, () -> {
            handshakeAttempts.incrementAndGet();
            throw new SSLHandshakeException("untrusted");
        })).isInstanceOf(SSLHandshakeException.class);
        assertThat(handshakeAttempts).hasValue(1);
    }

    private static final class RecordingSslSocketFactory extends SSLSocketFactory {
        private final RecordingSslSocket socket;

        private RecordingSslSocketFactory(RecordingSslSocket socket) {
            this.socket = socket;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return new String[0];
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return new String[0];
        }

        @Override
        public Socket createSocket() {
            return socket;
        }

        @Override
        public Socket createSocket(Socket ignored, String host, int port, boolean autoClose) {
            return socket;
        }

        @Override
        public Socket createSocket(String host, int port) {
            return socket;
        }

        @Override
        public Socket createSocket(String host, int port, java.net.InetAddress local, int localPort) {
            return socket;
        }

        @Override
        public Socket createSocket(java.net.InetAddress host, int port) {
            return socket;
        }

        @Override
        public Socket createSocket(
                java.net.InetAddress address,
                int port,
                java.net.InetAddress localAddress,
                int localPort) {
            return socket;
        }
    }

    private static final class RecordingSslSocket extends SSLSocket {
        private SocketAddress remoteAddress;
        private int connectTimeout;
        private int readTimeout;

        @Override
        public void connect(SocketAddress endpoint, int timeout) {
            remoteAddress = endpoint;
            connectTimeout = timeout;
        }

        @Override
        public void setSoTimeout(int timeout) {
            readTimeout = timeout;
        }

        @Override
        public String[] getSupportedCipherSuites() { return new String[0]; }

        @Override
        public String[] getEnabledCipherSuites() { return new String[0]; }

        @Override
        public void setEnabledCipherSuites(String[] suites) { }

        @Override
        public String[] getSupportedProtocols() { return new String[0]; }

        @Override
        public String[] getEnabledProtocols() { return new String[0]; }

        @Override
        public void setEnabledProtocols(String[] protocols) { }

        @Override
        public SSLSession getSession() { return null; }

        @Override
        public void addHandshakeCompletedListener(HandshakeCompletedListener listener) { }

        @Override
        public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) { }

        @Override
        public void startHandshake() { }

        @Override
        public void setUseClientMode(boolean mode) { }

        @Override
        public boolean getUseClientMode() { return true; }

        @Override
        public void setNeedClientAuth(boolean need) { }

        @Override
        public boolean getNeedClientAuth() { return false; }

        @Override
        public void setWantClientAuth(boolean want) { }

        @Override
        public boolean getWantClientAuth() { return false; }

        @Override
        public void setEnableSessionCreation(boolean flag) { }

        @Override
        public boolean getEnableSessionCreation() { return false; }
    }
}
