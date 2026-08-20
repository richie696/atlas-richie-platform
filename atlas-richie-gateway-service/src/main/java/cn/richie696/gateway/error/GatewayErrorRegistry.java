package cn.richie696.gateway.error;

import cn.richie696.gateway.error.codes.GatewayErrorCode;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网关错误码注册中心。
 * <p>
 * 提供错误码枚举与 {@link GatewayErrorEntry} 之间的双向查询，以及对外暴露
 * 全部错误码清单（供 JSON 端点、详情页 RouterFunction 等使用）。
 * </p>
 *
 * <h2>helpUrl 相对路径规则</h2>
 * <ul>
 *   <li>helpUrl 始终是相对路径 {@code /gateway/errors/{errorCode}}，不拼接 Host / Origin。</li>
 *   <li>前端可基于 {@code window.location.origin} 自行拼接绝对 URL。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-04
 */
public final class GatewayErrorRegistry {

    /**
     * 详情页相对路径前缀。
     */
    public static final String HELP_URL_PREFIX = "/gateway/errors/";

    /**
     * 列表页相对路径。
     */
    public static final String HELP_URL_INDEX = "/gateway/errors";

    /**
     * JSON 清单相对路径。
     */
    public static final String HELP_URL_JSON = "/gateway/errors.json";

    /**
     * 错误码 → 条目 映射（{@link LinkedHashMap} 保证遍历顺序与枚举声明一致）。
     */
    private static final Map<String, GatewayErrorEntry> ENTRIES_BY_CODE;

    /**
     * HTTP 状态码 → 首选错误码条目 映射（同状态码多个错误码时取首条）。
     */
    private static final Map<Integer, GatewayErrorEntry> ENTRY_BY_HTTP_STATUS;

    static {
        Map<String, GatewayErrorEntry> byCode = new LinkedHashMap<>();
        Map<Integer, GatewayErrorEntry> byStatus = new LinkedHashMap<>();
        for (GatewayErrorCode code : GatewayErrorCode.values()) {
            GatewayErrorEntry entry = new GatewayErrorEntry(
                    code.getErrorCode(),
                    code.getHttpStatus(),
                    code.isRetryable(),
                    code.getDocSlug(),
                    code.getI18nKey(),
                    code.getVersion(),
                    HELP_URL_PREFIX + code.getErrorCode()
            );
            byCode.put(code.getErrorCode(), entry);
            // 同状态码多个错误码时，首条胜出
            byStatus.putIfAbsent(code.getHttpStatus(), entry);
        }
        ENTRIES_BY_CODE = Collections.unmodifiableMap(byCode);
        ENTRY_BY_HTTP_STATUS = Collections.unmodifiableMap(byStatus);
    }

    private GatewayErrorRegistry() {
    }

    /**
     * 返回所有错误码条目，按枚举声明顺序排列。
     *
     * @return 不可变的错误码条目列表
     */
    public static List<GatewayErrorEntry> getAll() {
        return ENTRIES_BY_CODE.values().stream().collect(Collectors.toUnmodifiableList());
    }

    /**
     * 根据错误码字符串查找条目（不区分大小写）。
     *
     * @param code 错误码字符串
     * @return 对应的条目，若未匹配返回 {@code null}
     */
    public static GatewayErrorEntry getByCode(String code) {
        if (code == null) {
            return null;
        }
        return ENTRIES_BY_CODE.get(code.toUpperCase());
    }

    /**
     * 根据 HTTP 状态码返回首选错误码条目（同状态码多个错误码时取首条）。
     *
     * @param httpStatus HTTP 状态码
     * @return 对应的条目，若无匹配返回 {@code null}
     */
    public static GatewayErrorEntry getByHttpStatus(int httpStatus) {
        return ENTRY_BY_HTTP_STATUS.get(httpStatus);
    }

    /**
     * 根据错误码字符串返回文档 slug（错误码不存在时返回空字符串）。
     *
     * @param code 错误码字符串
     * @return slug（如 {@code gw-auth-token-invalid}），未匹配返回 {@code ""}
     */
    public static String getDocSlug(String code) {
        GatewayErrorEntry entry = getByCode(code);
        return entry != null ? entry.getDocSlug() : "";
    }

    /**
     * 根据错误码字符串返回详情页相对路径。
     *
     * @param code 错误码字符串
     * @return helpUrl，未匹配返回 {@code ""}
     */
    public static String getHelpUrl(String code) {
        GatewayErrorEntry entry = getByCode(code);
        return entry != null ? entry.getHelpUrl() : "";
    }

    /**
     * 根据错误码字符串返回对应的 {@link GatewayErrorCode} 枚举。
     * <p>
     * 仅做错误码字符串与枚举之间的桥接，不做 HTTP 状态码匹配。
     * </p>
     *
     * @param code 错误码字符串
     * @return 对应的枚举，未匹配返回 {@code null}
     */
    public static GatewayErrorCode fromCode(String code) {
        return GatewayErrorCode.fromCode(code);
    }

    /**
     * 返回错误码字符串数组（按枚举声明顺序），便于集合 / JSON 序列化。
     *
     * @return 错误码字符串数组
     */
    public static String[] allCodes() {
        return Arrays.stream(GatewayErrorCode.values())
                .map(GatewayErrorCode::getErrorCode)
                .toArray(String[]::new);
    }
}
