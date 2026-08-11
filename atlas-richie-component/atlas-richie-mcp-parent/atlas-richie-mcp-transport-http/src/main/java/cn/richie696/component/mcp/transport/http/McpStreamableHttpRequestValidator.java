package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.protocol.McpJsonRpcValidator;
import cn.richie696.component.mcp.protocol.McpMetaKeys;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 2026-07-28 Streamable HTTP POST 与镜像 Header 验证器。
 *
 * <p>现代 MCP Streamable HTTP 协议不只把方法/参数写在 JSON-RPC body 内，还在 HTTP 头中显式
 * 镜像若干字段以便中间件（网关、审计、负载均衡）无需解析 JSON 即可识别请求类型与目标资源。
 * 这种"header 与 body 双重携带"的协议约定要求服务端对二者做交叉校验——
 * {@link #validate(McpHttpRequest)} 正是这一职责的承担者。</p>
 *
 * <p>校验维度：
 * <ul>
 *   <li>HTTP 方法必须是 POST；</li>
 *   <li>{@code Origin} 头要么缺失要么通过 {@link McpOriginPolicy} 校验；</li>
 *   <li>{@code Content-Type} 必须为 {@code application/json}；</li>
 *   <li>{@code Accept} 必须同时包含 {@code application/json} 与 {@code text/event-stream}
 *       ——这是 Streamable HTTP 的双边契约，缺失则退化为"普通一次性 HTTP"，无法满足
 *       服务端以 SSE 推送 progress 通知的需求；</li>
 *   <li>{@code MCP-Protocol-Version} 与 {@code params._meta.protocolVersion} 必须一致且被支持；</li>
 *   <li>{@code Mcp-Method} / {@code Mcp-Name} 等镜像头必须与 body 同步。</li>
 *   <li>所有形参 Header 通过 {@code Mcp-Param-*} 前缀声明的形参镜像集合也必须与 body 严格对齐。</li>
 * </ul></p>
 *
 * <p>校验失败时抛 {@link McpHttpTransportException} 以便端点统一映射到对应 HTTP 状态码和
 * JSON-RPC 协议错误；{@link McpProtocolValidator} 也参与了 body 层 schema 检查（属于协议层校验）。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpStreamableHttpRequestValidator {
    private static final String JSON = "application/json";
    private static final String SSE = "text/event-stream";
    private static final String BASE64_PREFIX = "=?base64?";
    private static final String BASE64_SUFFIX = "?=";

    private final Set<String> supportedVersions;
    private final McpOriginPolicy originPolicy;

    /**
     * 构造校验器，初始化支持的协议版本集合与 Origin 校验策略。
     *
     * @param supportedVersions 端点支持的协议版本集合，不能为空
     * @param originPolicy      Origin 头判定策略
     * @throws IllegalArgumentException {@code supportedVersions} 为 null 或空集合
     */
    public McpStreamableHttpRequestValidator(
            Set<String> supportedVersions,
            McpOriginPolicy originPolicy) {
        if (supportedVersions == null || supportedVersions.isEmpty()) {
            throw new IllegalArgumentException("supportedVersions must not be empty");
        }
        this.supportedVersions = Set.copyOf(supportedVersions);
        this.originPolicy = Objects.requireNonNull(originPolicy, "originPolicy");
    }

    /**
     * 对一条 MCP 入站请求做完整校验：HTTP 方法、Content-Type/Accept、Origin、协议版本一致性、
     * body 与镜像 Header 的对齐、形参 Header 等。
     *
     * <p>校验顺序经过精心设计：先校验低成本的 HTTP 层头部，再校验协议层 body 字段，最后
     * 比较二者是否一致。这让攻击者难以通过简单 Header 触发昂贵的参数校验。</p>
     *
     * @param request 入站请求
     * @return 已通过校验的请求
     * @throws McpHttpTransportException 入参不满足 HTTP/Streamable/协议版本要求
     * @throws McpProtocolException      body 不满足协议层 schema（在内部被包装为 transport 错误）
     */
    public McpValidatedHttpRequest validate(McpHttpRequest request) {
        Objects.requireNonNull(request, "request");
        if (!"POST".equalsIgnoreCase(request.httpMethod())) {
            throw transportError(405, "MCP endpoint only accepts POST");
        }
        HeaderBag headers = new HeaderBag(request.headers());
        validateOrigin(headers);
        requireMediaType(headers, McpHttpHeaders.CONTENT_TYPE, JSON, 415);
        requireAccept(headers);

        McpJsonRpcRequest message = request.message();
        try {
            McpJsonRpcValidator.validate(message);
        } catch (McpProtocolException exception) {
            throw new McpHttpTransportException(400, exception.getMessage(), exception);
        }
        String bodyVersion = bodyProtocolVersion(message);
        String headerVersion = requireSingle(headers, McpHttpHeaders.PROTOCOL_VERSION);
        requireEqual(McpHttpHeaders.PROTOCOL_VERSION, headerVersion, bodyVersion);
        if (!supportedVersions.contains(headerVersion)) {
            throw unsupportedVersion(headerVersion);
        }

        String headerMethod = requireSingle(headers, McpHttpHeaders.METHOD);
        requireEqual(McpHttpHeaders.METHOD, headerMethod, message.method());
        validateName(headers, message);
        validateParameterHeaders(headers, message);
        return new McpValidatedHttpRequest(headerVersion, message);
    }

    /**
     * 校验 {@code Origin} 头。
     *
     * <p>策略：
     * <ul>
     *   <li>缺失视为非浏览器客户端，放行；</li>
     *   <li>出现多个 Origin 头视为非法——HTTP 规范不允许；</li>
     *   <li>Origin 为空白字符串同样视为非法；</li>
     *   <li>最终必须由 {@link McpOriginPolicy} 决定是否放行。</li>
     * </ul></p>
     *
     * @throws McpHttpTransportException 状态码 403
     */
    private void validateOrigin(HeaderBag headers) {
        List<String> origins = headers.values(McpHttpHeaders.ORIGIN);
        if (origins.isEmpty()) {
            return;
        }
        if (origins.size() != 1 || origins.getFirst().isBlank()
                || !originPolicy.isAllowed(origins.getFirst())) {
            throw transportError(403, "Origin is not allowed");
        }
    }

    /**
     * {@code Accept} 头校验：必须同时包含 JSON 和 SSE 媒体类型。
     *
     * <p>这是 Streamable HTTP 的核心契约——客户端必须先承诺能处理两种响应形式，否则
     * 服务端无权选择这两者间的任一种回包形式。</p>
     */
    private void requireAccept(HeaderBag headers) {
        List<String> accepted = commaSeparated(headers.values(McpHttpHeaders.ACCEPT));
        boolean json = accepted.stream().anyMatch(value -> mediaType(value).equals(JSON));
        boolean sse = accepted.stream().anyMatch(value -> mediaType(value).equals(SSE));
        if (!json || !sse) {
            throw transportError(
                    406,
                    "Accept must include application/json and text/event-stream");
        }
    }

    /**
     * 通用 Content-Type 等媒体类型校验。
     */
    private void requireMediaType(
            HeaderBag headers,
            String name,
            String expected,
            int status) {
        String value = requireSingleTransport(headers, name, status);
        if (!mediaType(value).equals(expected)) {
            throw transportError(status, name + " must be " + expected);
        }
    }

    /**
     * 从 {@code params._meta.protocolVersion} 中取出协议版本。
     */
    private String bodyProtocolVersion(McpJsonRpcRequest message) {
        Object metaValue = message.params().get("_meta");
        if (!(metaValue instanceof Map<?, ?> meta)) {
            throw headerMismatch("Request body is missing params._meta");
        }
        Object version = meta.get(McpMetaKeys.PROTOCOL_VERSION);
        if (!(version instanceof String text) || text.isBlank()) {
            throw headerMismatch("Request body is missing protocol version metadata");
        }
        return text;
    }

    /**
     * 校验 {@code Mcp-Name} 镜像 Header：对 {@code tools/call} / {@code prompts/get} 取
     * {@code params.name}，对 {@code resources/read} 取 {@code params.uri}。
     */
    private void validateName(HeaderBag headers, McpJsonRpcRequest message) {
        String sourceField = switch (message.method()) {
            case "tools/call", "prompts/get" -> "name";
            case "resources/read" -> "uri";
            default -> null;
        };
        if (sourceField == null) {
            return;
        }
        Object bodyName = message.params().get(sourceField);
        if (!(bodyName instanceof String expected) || expected.isBlank()) {
            throw headerMismatch("Request body is missing params." + sourceField);
        }
        String encoded = requireSingle(headers, McpHttpHeaders.NAME);
        String decoded = decodeHeaderValue(McpHttpHeaders.NAME, encoded);
        requireEqual(McpHttpHeaders.NAME, decoded, expected);
    }

    /**
     * 校验 {@code Mcp-Param-*} 镜像 Header 集合。
     *
     * <p>每个 Header 必须满足三件事：
     * (a) 形参名必须匹配 {@code [A-Za-z0-9-]+}（ASCII 可见字符，仅连字符）；</p>
     * <p>(b) 仅出现一次（多值视为非法）；</p>
     * <p>(c) 在 body {@code params.arguments} 中大小写不敏感地命中相同 key，
     * 并要求值类型为 String/Number/Boolean 之一——结构化对象无法在 Header 中表达。</p>
     */
    private void validateParameterHeaders(HeaderBag headers, McpJsonRpcRequest message) {
        if (!(message.params().get("arguments") instanceof Map<?, ?> arguments)) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : headers.entries()) {
            String headerName = entry.getKey();
            if (!headerName.toLowerCase(Locale.ROOT)
                    .startsWith(McpHttpHeaders.PARAMETER_PREFIX.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String parameter = headerName.substring(McpHttpHeaders.PARAMETER_PREFIX.length());
            if (parameter.isBlank() || !parameter.matches("[A-Za-z0-9-]+") || entry.getValue().size() != 1) {
                throw headerMismatch(headerName + " must contain exactly one valid parameter value");
            }
            Object argumentValue = arguments.entrySet().stream()
                    .filter(candidate -> candidate.getKey() instanceof String key
                            && key.equalsIgnoreCase(parameter))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElseThrow(() -> headerMismatch(
                            "Unknown or missing request argument for " + headerName));
            String expected = parameterValue(argumentValue, headerName);
            String actual = decodeHeaderValue(headerName, entry.getValue().getFirst());
            requireEqual(headerName, actual, expected);
        }
    }

    /**
     * 将形参值规范化为字符串：{@code String} 原样返回；{@code Number}/{@code Boolean} 通过
     * {@link String#valueOf}；其余类型非法（无法表达在 Header 中）。
     */
    private String parameterValue(Object value, String headerName) {
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        throw headerMismatch(headerName + " can only mirror string, number or boolean arguments");
    }

    /**
     * 要求 Header 恰好出现一次，且非空。失败抛出 Header mismatch（400）。
     */
    private String requireSingle(HeaderBag headers, String name) {
        List<String> values = headers.values(name);
        if (values.size() != 1 || values.getFirst().isBlank()) {
            throw headerMismatch(name + " must occur exactly once and must not be blank");
        }
        return values.getFirst();
    }

    /**
     * 与 {@link #requireSingle} 类似，但允许调用方决定使用哪个 HTTP 状态码——用于 Content-Type
     * 错误返回 415 而非其他 4xx 的场景。
     */
    private String requireSingleTransport(HeaderBag headers, String name, int status) {
        List<String> values = headers.values(name);
        if (values.size() != 1 || values.getFirst().isBlank()) {
            throw transportError(status, name + " must occur exactly once");
        }
        return values.getFirst();
    }

    /**
     * 头部值必须与 body 值一致；不一致抛 Header mismatch（400），响应体的 error.code 为 -32020。
     */
    private void requireEqual(String name, String headerValue, String bodyValue) {
        if (!headerValue.equals(bodyValue)) {
            throw headerMismatch(name + " does not match the request body");
        }
    }

    /**
     * 解码一个镜像 Header 值。支持普通 ASCII 值（{@link #safePlainHeader} 校验）
     * 和 {@code =?base64?...?= base64 编码}两种形态。
     *
     * <p>Base64 模式存在的意义：镜像 Header 字段（如形参值）可能含非 ASCII 字符或控制字符，
     * 用 {@code =?base64?<base64>?=} 哨兵包装后通过 Base64 + UTF-8 严格解码，
     * 可以让任意字节安全地穿过纯 ASCII Header 通道。</p>
     */
    private String decodeHeaderValue(String name, String value) {
        if (!matchesBase64Sentinel(value)) {
            if (!safePlainHeader(value)) {
                throw headerMismatch(name + " contains invalid characters");
            }
            return value;
        }
        String payload = value.substring(
                BASE64_PREFIX.length(),
                value.length() - BASE64_SUFFIX.length());
        try {
            byte[] decoded = Base64.getDecoder().decode(payload);
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(decoded))
                    .toString();
        } catch (IllegalArgumentException | CharacterCodingException exception) {
            throw headerMismatch(name + " contains malformed Base64");
        }
    }

    /**
     * 普通镜像 Header 字符安全校验：
     * <ul>
     *   <li>不允许前后空白；</li>
     *   <li>不允许落在 Base64 哨兵内（避免混淆）；</li>
     *   <li>字符范围：{@code 0x20-0x7E}（可见 ASCII）以及水平制表符（{@code \t}）。</li>
     * </ul></p>
     */
    private boolean safePlainHeader(String value) {
        if (!value.equals(value.strip()) || matchesBase64Sentinel(value)) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if ((character < 0x20 && character != '\t') || character > 0x7e) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断 Header 值是否由 {@code =?base64?...?=} 哨兵包裹。
     */
    private boolean matchesBase64Sentinel(String value) {
        return value.startsWith(BASE64_PREFIX) && value.endsWith(BASE64_SUFFIX);
    }

    /**
     * 把 {@code Accept} 头可能的多值并列（如 {@code "a, b; q=0.9, c"}）拆解为干净的列表：
     * 去除空白与参数（{@code ;} 后内容），保留核心 media-type；空白 token 直接丢弃。
     */
    private List<String> commaSeparated(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            for (String part : value.split(",")) {
                if (!part.isBlank()) {
                    result.add(part.strip());
                }
            }
        }
        return result;
    }

    /**
     * 从 {@code "type/subtype; params"} 串中抽出 media-type 主类型。
     */
    private String mediaType(String value) {
        int parameters = value.indexOf(';');
        return (parameters < 0 ? value : value.substring(0, parameters))
                .strip()
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 构造协议版本不被支持的错误，code = -32022。
     */
    private McpHttpTransportException unsupportedVersion(String requested) {
        McpProtocolException error = new McpProtocolException(
                "MCP_UNSUPPORTED_PROTOCOL_VERSION",
                -32022,
                "Unsupported protocol version",
                Map.of(
                        "supported", supportedVersions.stream().sorted().toList(),
                        "requested", requested));
        return new McpHttpTransportException(400, error.getMessage(), error);
    }

    /**
     * 构造"Header 与 body 不一致"的错误，code = -32020。
     */
    private McpHttpTransportException headerMismatch(String message) {
        McpProtocolException error = new McpProtocolException(
                "MCP_HEADER_MISMATCH",
                -32020,
                "Header mismatch: " + message,
                Map.of());
        return new McpHttpTransportException(400, error.getMessage(), error);
    }

    /**
     * 通用 transport 错误工厂——不带协议层 error 对象。
     */
    private McpHttpTransportException transportError(int status, String message) {
        return new McpHttpTransportException(status, message, null);
    }

    /**
     * 不区分大小写且保留多值的请求头容器。
     *
     * <p>内部使用 {@link LinkedHashMap} 模拟对原始头集合的小型索引，避免重复遍历；所有
     * 公共访问方法返回不可变视图。这是为校验而存在的微型工具类，而非通用头管理。</p>
     */
    private static final class HeaderBag {
        private final Map<String, List<String>> values = new LinkedHashMap<>();

        /**
         * 构造时把所有头名归一化为小写，方便后续大小写不敏感查询。
         */
        private HeaderBag(Map<String, List<String>> headers) {
            headers.forEach((name, headerValues) -> values
                    .computeIfAbsent(name.toLowerCase(Locale.ROOT), ignored -> new ArrayList<>())
                    .addAll(headerValues));
        }

        /**
         * 取某个 header 名下所有值。返回不可变副本避免外部修改污染内部状态。
         */
        private List<String> values(String name) {
            return List.copyOf(values.getOrDefault(
                    name.toLowerCase(Locale.ROOT),
                    List.of()));
        }

        /**
         * 暴露头集合的 Entry 集不可变快照供校验遍历。
         */
        private Set<Map.Entry<String, List<String>>> entries() {
            return Set.copyOf(values.entrySet());
        }
    }
}
