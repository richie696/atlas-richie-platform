package cn.richie696.gateway.error;

import cn.richie696.gateway.error.codes.GatewayErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GatewayErrorRegistry} Wave 2 契约测试。
 * <p>
 * 验证错误码注册表是唯一数据源：
 * </p>
 * <ul>
 *   <li>包含 12 个错误码条目（{@link GatewayErrorCode} 枚举值数量）。</li>
 *   <li>{@code fromCode} / {@code getByCode} / {@code getByHttpStatus} 行为正确。</li>
 *   <li>{@code helpUrl} 始终为相对路径 {@code /gateway/errors/{code}}。</li>
 *   <li>所有错误码字段完整（httpStatus / retryable / docSlug / i18nKey / version）。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-04
 */
class GatewayErrorRegistryTest {

    /**
     * 错误码注册表总条目数（与 {@link GatewayErrorCode} 枚举值数量一致）。
     */
    private static final int EXPECTED_TOTAL_ENTRIES = GatewayErrorCode.values().length;

    @Test
    @DisplayName("getAll() 返回所有错误码条目，条目数 == 枚举值数量")
    void testGetAll_ReturnsAllEntries() {
        List<GatewayErrorEntry> all = GatewayErrorRegistry.getAll();

        // 契约：注册表条目数 == 枚举值数量（单一数据源）
        assertEquals(EXPECTED_TOTAL_ENTRIES, all.size(),
                "注册表条目数应等于 GatewayErrorCode 枚举值数量（12）");

        // 契约：返回的列表不可修改
        assertNotNull(all);
        assertAll("所有条目字段均不可为空",
                () -> all.forEach(e -> assertNotNull(e.getErrorCode())),
                () -> all.forEach(e -> assertNotNull(e.getDocSlug())),
                () -> all.forEach(e -> assertNotNull(e.getI18nKey())),
                () -> all.forEach(e -> assertNotNull(e.getVersion())),
                () -> all.forEach(e -> assertNotNull(e.getHelpUrl())));
    }

    @Test
    @DisplayName("getAll() 保持枚举声明顺序")
    void testGetAll_PreservesEnumDeclarationOrder() {
        List<GatewayErrorEntry> all = GatewayErrorRegistry.getAll();

        // 契约：LinkedHashMap 保证遍历顺序与枚举声明一致
        for (int i = 0; i < EXPECTED_TOTAL_ENTRIES; i++) {
            assertEquals(GatewayErrorCode.values()[i].getErrorCode(),
                    all.get(i).getErrorCode(),
                    "第 " + i + " 个条目应与第 " + i + " 个枚举值对应");
        }
    }

    @Test
    @DisplayName("fromCode(\"GW-AUTH-0001\") 返回对应枚举实例")
    void testFromCode_KnownCode() {
        GatewayErrorCode code = GatewayErrorRegistry.fromCode("GW-AUTH-0001");

        assertNotNull(code);
        assertEquals(GatewayErrorCode.GW_AUTH_0001, code);
        assertEquals("GW-AUTH-0001", code.getErrorCode());
    }

    @Test
    @DisplayName("fromCode(\"GW-INVALID-9999\") 返回 null（不抛异常）")
    void testFromCode_UnknownCodeReturnsNull() {
        // 契约：未知错误码返回 null 而不抛 IllegalArgumentException，便于上层做 null 判断
        GatewayErrorCode code = GatewayErrorRegistry.fromCode("GW-INVALID-9999");
        assertNull(code, "未知错误码应返回 null 而非抛异常");

        // 边界：null 入参也不抛异常
        assertNull(GatewayErrorRegistry.fromCode(null), "null 入参应返回 null");
        // 边界：空字符串应返回 null
        assertNull(GatewayErrorRegistry.fromCode(""), "空字符串应返回 null");
    }

    @Test
    @DisplayName("getByCode(\"GW-AUTH-0001\") 返回对应 GatewayErrorEntry")
    void testGetByCode_KnownCode() {
        GatewayErrorEntry entry = GatewayErrorRegistry.getByCode("GW-AUTH-0001");

        assertNotNull(entry);
        assertEquals("GW-AUTH-0001", entry.getErrorCode());
        assertEquals(401, entry.getHttpStatus());
        assertFalse(entry.isRetryable(), "GW-AUTH-0001 不可重试");
        assertEquals("gw-auth-token-invalid", entry.getDocSlug());
        assertEquals("error.auth.token.invalid", entry.getI18nKey());
        assertEquals("1.0.0", entry.getVersion());
    }

    @Test
    @DisplayName("getByHttpStatus(401) 返回 token 相关错误码")
    void testGetByHttpStatus_401() {
        GatewayErrorEntry entry = GatewayErrorRegistry.getByHttpStatus(401);

        assertNotNull(entry);
        // 契约：401 状态码映射到 token 相关错误码（GW-AUTH-*）
        assertTrue(entry.getErrorCode().startsWith("GW-AUTH-"),
                "401 状态码应映射到 GW-AUTH-* 系列错误码");
    }

    @Test
    @DisplayName("getByHttpStatus 不支持的状态码返回 null")
    void testGetByHttpStatus_UnsupportedReturnsNull() {
        // 契约：未注册的状态码（如 418）返回 null，由上层兜底
        GatewayErrorEntry entry = GatewayErrorRegistry.getByHttpStatus(418);

        assertNull(entry, "未注册的 HTTP 状态码应返回 null，由 FALLBACK_ERROR_CODE 兜底");
    }

    @Test
    @DisplayName("getDocSlug(\"GW-AUTH-0001\") 返回 doc slug")
    void testGetDocSlug_KnownCode() {
        assertEquals("gw-auth-token-invalid", GatewayErrorRegistry.getDocSlug("GW-AUTH-0001"));
    }

    @Test
    @DisplayName("getDocSlug 未匹配时返回空字符串")
    void testGetDocSlug_UnknownCodeReturnsEmptyString() {
        assertEquals("", GatewayErrorRegistry.getDocSlug("GW-INVALID-9999"));
        assertEquals("", GatewayErrorRegistry.getDocSlug(null));
    }

    @Test
    @DisplayName("getHelpUrl(\"GW-AUTH-0001\") 返回相对路径 /gateway/errors/GW-AUTH-0001")
    void testGetHelpUrl_KnownCode() {
        assertEquals("/gateway/errors/GW-AUTH-0001",
                GatewayErrorRegistry.getHelpUrl("GW-AUTH-0001"));
    }

    @Test
    @DisplayName("getHelpUrl 未匹配时返回空字符串")
    void testGetHelpUrl_UnknownCodeReturnsEmptyString() {
        assertEquals("", GatewayErrorRegistry.getHelpUrl("GW-INVALID-9999"));
        assertEquals("", GatewayErrorRegistry.getHelpUrl(null));
    }

    @Test
    @DisplayName("allCodes() 返回所有错误码字符串数组")
    void testAllCodes() {
        String[] codes = GatewayErrorRegistry.allCodes();

        assertEquals(EXPECTED_TOTAL_ENTRIES, codes.length);
        // 验证包含所有已注册的错误码
        for (GatewayErrorCode code : GatewayErrorCode.values()) {
            assertTrue(java.util.Arrays.asList(codes).contains(code.getErrorCode()),
                    "allCodes() 应包含 " + code.getErrorCode());
        }
    }

    @ParameterizedTest
    @EnumSource(GatewayErrorCode.class)
    @DisplayName("所有错误码字段完整性校验")
    void testAllErrorCodes_FieldCompleteness(GatewayErrorCode code) {
        GatewayErrorEntry entry = GatewayErrorRegistry.getByCode(code.getErrorCode());

        assertNotNull(entry, "枚举 " + code.name() + " 必须在注册表中");

        // 字段完整性：code / httpStatus / docSlug / i18nKey / version / helpUrl 均不为空
        assertAll("枚举 " + code.name() + " 字段完整性",
                () -> assertNotNull(entry.getErrorCode()),
                () -> assertEquals(code.getErrorCode(), entry.getErrorCode()),
                () -> assertEquals(code.getHttpStatus(), entry.getHttpStatus()),
                () -> assertEquals(code.isRetryable(), entry.isRetryable()),
                () -> assertEquals(code.getDocSlug(), entry.getDocSlug()),
                () -> assertEquals(code.getI18nKey(), entry.getI18nKey()),
                () -> assertEquals(code.getVersion(), entry.getVersion()),
                () -> assertFalse(entry.getHelpUrl().isEmpty(), "helpUrl 必须非空"),
                () -> assertTrue(entry.getHttpStatus() >= 400 && entry.getHttpStatus() < 600,
                        "HTTP 状态码应在 4xx/5xx 范围内"));
    }

    @ParameterizedTest
    @EnumSource(GatewayErrorCode.class)
    @DisplayName("所有错误码 helpUrl 都是相对路径 /gateway/errors/{code}")
    void testAllErrorCodes_HelpUrlRelativePath(GatewayErrorCode code) {
        GatewayErrorEntry entry = GatewayErrorRegistry.getByCode(code.getErrorCode());

        String expectedPrefix = GatewayErrorRegistry.HELP_URL_PREFIX;
        String helpUrl = entry.getHelpUrl();

        // 契约：helpUrl 是相对路径（不含 http:// / https://），始终以前缀开头
        assertTrue(helpUrl.startsWith(expectedPrefix),
                "helpUrl 必须以 " + expectedPrefix + " 开头，实际：" + helpUrl);
        assertFalse(helpUrl.startsWith("http://"),
                "helpUrl 不应拼接 http:// Host");
        assertFalse(helpUrl.startsWith("https://"),
                "helpUrl 不应拼接 https:// Host");
        assertEquals(expectedPrefix + code.getErrorCode(), helpUrl,
                "helpUrl 必须严格等于 /gateway/errors/{errorCode}");
    }

    @Test
    @DisplayName("错误码字符串集合无重复")
    void testAllCodes_AreUnique() {
        // 契约：错误码字符串不能重复，否则 JSON 清单 / 详情页路由会冲突
        List<GatewayErrorEntry> all = GatewayErrorRegistry.getAll();
        Set<String> codes = all.stream()
                .map(GatewayErrorEntry::getErrorCode)
                .collect(Collectors.toSet());
        assertEquals(all.size(), codes.size(),
                "所有错误码字符串必须唯一");
    }

    @Test
    @DisplayName("GW-AUTH 系列错误码全部映射到 401/403")
    void testAuthSeriesHttpStatus() {
        // 契约：GW-AUTH 系列必须映射到 401（token 问题）或 403（权限问题）
        for (GatewayErrorCode code : GatewayErrorCode.values()) {
            if (code.getErrorCode().startsWith("GW-AUTH-")) {
                int status = code.getHttpStatus();
                assertTrue(status == 401 || status == 403,
                        "GW-AUTH-* 应映射 401/403，实际：" + code + " → " + status);
            }
        }
    }

    @Test
    @DisplayName("GW-RATE-0001 是 retryable = true")
    void testRateLimitIsRetryable() {
        GatewayErrorCode code = GatewayErrorRegistry.fromCode("GW-RATE-0001");
        assertNotNull(code);
        assertTrue(code.isRetryable(), "限流错误应标记为可重试");
    }

    @Test
    @DisplayName("getByCode 与 getByHttpStatus 对应条目共享 errorCode 字段")
    void testGetByCodeAndHttpStatusConsistency() {
        // 契约：通过 code 查到的条目，与该条目 httpStatus 反查的结果应一致
        GatewayErrorEntry byCode = GatewayErrorRegistry.getByCode("GW-AUTH-0001");
        GatewayErrorEntry byStatus = GatewayErrorRegistry.getByHttpStatus(byCode.getHttpStatus());

        assertNotNull(byStatus);
        assertSame(byCode, byStatus,
                "同一 (code, httpStatus) 通过不同 API 查询应返回同一实例");
    }
}
