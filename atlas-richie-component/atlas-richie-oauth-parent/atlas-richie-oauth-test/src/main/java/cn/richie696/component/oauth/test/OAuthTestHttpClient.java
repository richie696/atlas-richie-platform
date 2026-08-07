package cn.richie696.component.oauth.test;

import cn.richie696.component.oauth.contract.model.OAuthAuthorizationRequest;
import cn.richie696.component.oauth.contract.model.OAuthTokenRequest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 面向 OAuth Server 的最小黑盒 HTTP 客户端。
 *
 * <p>不跟随重定向，便于测试授权码回调中的 code/state，也不负责 JSON 反序列化，
 * 这样服务可以自由使用 Jackson、WebTestClient 或自己的响应模型。</p>
 */
public final class OAuthTestHttpClient {

    private final URI baseUri;
    private final HttpClient httpClient;
    private final Duration timeout;

    public OAuthTestHttpClient(URI baseUri) {
        this(baseUri, Duration.ofSeconds(10));
    }

    public OAuthTestHttpClient(URI baseUri, Duration timeout) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(resolve(path))
                .timeout(timeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> postForm(String path, Map<String, String> form)
            throws IOException, InterruptedException {
        String body = form.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(resolve(path))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> authorize(String path, OAuthAuthorizationRequest request)
            throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder(
                        OAuthTestHttp.authorizationUri(resolve(path), request))
                .timeout(timeout)
                .header("Accept", "text/html, application/json")
                .GET()
                .build();
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> token(String path, OAuthTokenRequest request)
            throws IOException, InterruptedException {
        return postForm(path, OAuthTestHttp.tokenForm(request));
    }

    public HttpResponse<String> introspect(String path, String token,
                                           String clientId, String clientSecret)
            throws IOException, InterruptedException {
        return postForm(path, Map.of(
                "token", token,
                "client_id", clientId,
                "client_secret", clientSecret));
    }

    public HttpResponse<String> revoke(String path, String token,
                                       String clientId, String clientSecret,
                                       String tokenTypeHint)
            throws IOException, InterruptedException {
        Map<String, String> form = new java.util.LinkedHashMap<>();
        form.put("token", token);
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        if (tokenTypeHint != null) {
            form.put("token_type_hint", tokenTypeHint);
        }
        return postForm(path, form);
    }

    private URI resolve(String path) {
        Objects.requireNonNull(path, "path");
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return URI.create(path);
        }
        String base = baseUri.toString().endsWith("/")
                ? baseUri.toString().substring(0, baseUri.toString().length() - 1)
                : baseUri.toString();
        return URI.create(base + (path.startsWith("/") ? path : "/" + path));
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
