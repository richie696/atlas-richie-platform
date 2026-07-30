package cn.richie696.antivirus.download;

import cn.richie696.antivirus.config.AntivirusProperties;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/** 阻止扫描下载接口访问本机、内网、链路本地和保留地址。 */
@Component
public class PublicUrlValidator {
    private final AntivirusProperties properties;

    public PublicUrlValidator(AntivirusProperties properties) {
        this.properties = properties;
    }

    public void validate(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean allowedScheme = "https".equals(scheme)
                || (properties.getDownload().isAllowHttp() && "http".equals(scheme));
        if (!allowedScheme) {
            throw new IllegalArgumentException("下载地址协议不允许");
        }
        if (uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("下载地址格式无效");
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            if (addresses.length == 0) {
                throw new IllegalArgumentException("下载地址无法解析");
            }
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    throw new IllegalArgumentException("下载地址不能指向非公网地址");
                }
            }
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("下载地址无法解析", exception);
        }
    }

    boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            return first != 0
                    && first != 10
                    && first != 127
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 169 && second == 254)
                    && !(first == 172 && second >= 16 && second <= 31)
                    && !(first == 192 && second == 0 && third == 0)
                    && !(first == 192 && second == 0 && third == 2)
                    && !(first == 192 && second == 168)
                    && !(first == 198 && (second == 18 || second == 19))
                    && !(first == 198 && second == 51 && third == 100)
                    && !(first == 203 && second == 0 && third == 113)
                    && first < 224;
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            boolean uniqueLocal = (first & 0xfe) == 0xfc;
            boolean documentation = first == 0x20 && second == 0x01
                    && Byte.toUnsignedInt(bytes[2]) == 0x0d
                    && Byte.toUnsignedInt(bytes[3]) == 0xb8;
            return !uniqueLocal && !documentation;
        }
        return false;
    }
}
