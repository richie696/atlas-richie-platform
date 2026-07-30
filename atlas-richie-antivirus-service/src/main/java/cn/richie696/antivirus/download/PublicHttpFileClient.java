package cn.richie696.antivirus.download;

import cn.richie696.antivirus.config.AntivirusProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

/** 只读取经过公网地址校验的响应流；不自动跟随未经再次校验的重定向。 */
@Component
public class PublicHttpFileClient {
    private final AntivirusProperties properties;
    private final PublicUrlValidator urlValidator;
    private final HttpClient httpClient;

    public PublicHttpFileClient(AntivirusProperties properties, PublicUrlValidator urlValidator) {
        this.properties = properties;
        this.urlValidator = urlValidator;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getDownload().getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public <T> DownloadResult<T> read(String downloadUrl, long maxBytes,
                                      InputStreamHandler<T> handler) throws IOException {
        URI current;
        try {
            current = URI.create(downloadUrl);
        } catch (IllegalArgumentException exception) {
            throw new IOException("下载地址格式无效", exception);
        }

        for (int redirects = 0; redirects <= properties.getDownload().getMaxRedirects(); redirects++) {
            urlValidator.validate(current);
            HttpRequest request = HttpRequest.newBuilder(current)
                    .GET()
                    .timeout(properties.getDownload().getRequestTimeout())
                    .header("Accept-Encoding", "identity")
                    .build();
            HttpResponse<InputStream> response = send(request);
            int status = response.statusCode();
            if (isRedirect(status)) {
                try (InputStream ignored = response.body()) {
                    String location = response.headers().firstValue("Location")
                            .orElseThrow(() -> new IOException("重定向响应缺少 Location"));
                    current = current.resolve(location);
                    continue;
                }
            }
            if (status != 200) {
                response.body().close();
                throw new IOException("下载地址返回非成功状态：" + status);
            }
            String contentEncoding = response.headers().firstValue("Content-Encoding").orElse("identity");
            if (!contentEncoding.isBlank() && !"identity".equalsIgnoreCase(contentEncoding)) {
                response.body().close();
                throw new IOException("下载响应不允许内容编码：" + contentEncoding);
            }
            Optional<Long> contentLength = response.headers().firstValueAsLong("Content-Length").stream()
                    .boxed().findFirst();
            if (contentLength.isPresent() && contentLength.get() > maxBytes) {
                response.body().close();
                throw new IOException("文件超过扫描大小限制");
            }
            try (InputStream body = response.body()) {
                T value = handler.handle(body, sourceName(current));
                return new DownloadResult<>(
                        value,
                        response.headers().firstValue("ETag").orElse(null),
                        contentLength.orElse(null));
            }
        }
        throw new IOException("下载地址重定向次数过多");
    }

    private HttpResponse<InputStream> send(HttpRequest request) throws IOException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("下载文件被中断", exception);
        }
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private String sourceName(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank() || path.endsWith("/")) {
            return "download";
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }

    @FunctionalInterface
    public interface InputStreamHandler<T> {
        T handle(InputStream input, String sourceName) throws IOException;
    }

    public record DownloadResult<T>(T value, String etag, Long contentLength) {
    }
}
