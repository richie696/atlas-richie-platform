package com.richie.testing.local;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

final class TcpPortProbe {

    private TcpPortProbe() {
    }

    static boolean isOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
