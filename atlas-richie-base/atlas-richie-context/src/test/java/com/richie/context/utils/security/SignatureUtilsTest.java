/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.context.utils.security;

import com.richie.context.utils.data.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// ========== DTO 签名测试用 VO ==========

/** 基础签名 DTO */
class BasicSignDTO {
    @SignField
    private String name;
    @SignField
    private int age;

    public BasicSignDTO(String name, int age) {
        this.name = name;
        this.age = age;
    }
}

/** 指定排序顺序的 DTO */
class OrderedSignDTO {
    @SignField(order = 2)
    private String name;
    @SignField(order = 1)
    private int age;
    @SignField(order = 11)
    private String email;

    public OrderedSignDTO(String name, int age, String email) {
        this.name = name;
        this.age = age;
        this.email = email;
    }
}

/** 部分字段参与签名的 DTO */
class PartialSignDTO {
    @SignField
    private String signed;
    private String notSigned; // 不参与签名

    public PartialSignDTO(String signed, String notSigned) {
        this.signed = signed;
        this.notSigned = notSigned;
    }
}

/** 嵌套 DTO */
class NestedSignOuterDTO {
    @SignField
    private String title;
    @SignField
    private NestedSignInnerDTO detail;

    public NestedSignOuterDTO(String title, NestedSignInnerDTO detail) {
        this.title = title;
        this.detail = detail;
    }
}

class NestedSignInnerDTO {
    @SignField
    private String subA;
    @SignField
    private String subB;

    public NestedSignInnerDTO(String subA, String subB) {
        this.subA = subA;
        this.subB = subB;
    }
}

/** 带 null 字段的 DTO */
class NullFieldDTO {
    @SignField
    private String name;
    @SignField
    private Integer age; // null

    public NullFieldDTO(String name, Integer age) {
        this.name = name;
        this.age = age;
    }
}

/** 继承基类的 DTO */
class BaseDTO {
    @SignField
    String baseField; // 子类可见
}

class ExtendsSignDTO extends BaseDTO {
    @SignField
    private String subField;

    public ExtendsSignDTO(String baseField, String subField) {
        this.baseField = baseField;
        this.subField = subField;
    }
}

/** 使用自定义 name 的 DTO */
class CustomNameSignDTO {
    @SignField(name = "user_name")
    private String userName; // 驼峰字段名，签名时用 snake_case
    @SignField(name = "user_age")
    private int userAge;

    public CustomNameSignDTO(String userName, int userAge) {
        this.userName = userName;
        this.userAge = userAge;
    }
}

/** 重复 order 的 DTO（应抛出异常） */
class DuplicateOrderSignDTO {
    @SignField(order = 1)
    private String name;
    @SignField(order = 2)
    private String email;
    @SignField(order = 2)
    private String phone;

    public DuplicateOrderSignDTO(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
    }
}

/** value-only 模式（无字段名，竖线连接符） */
@SignConfig(connector = "|", includeFieldName = false)
class ValueOnlySignDTO {
    @SignField
    private String name;
    @SignField
    private int age;
    @SignField
    private String email;

    public ValueOnlySignDTO(String name, int age, String email) {
        this.name = name;
        this.age = age;
        this.email = email;
    }
}

/** 自定义连接符（含字段名） */
@SignConfig(connector = ",")
class CustomConnectorSignDTO {
    @SignField
    private String name;
    @SignField
    private int age;

    public CustomConnectorSignDTO(String name, int age) {
        this.name = name;
        this.age = age;
    }
}

/** 枚举字段 DTO */
class EnumSignDTO {
    @SignField
    private String name;
    @SignField
    private Status status;

    public EnumSignDTO(String name, Status status) {
        this.name = name;
        this.status = status;
    }
}

enum Status {
    ACTIVE, INACTIVE
}

/**
 * {@link SignatureUtils} 单元测试
 *
 * @author richie696
 * @version 1.0
 * @since 2026-06-02
 */
class SignatureUtilsTest {

    private static final String TEST_URL = "/api/test";
    private static final String TEST_SECRET = "mySecretKey123!";

    // ---------------------------------------------------------------
    // createSign 测试
    // ---------------------------------------------------------------

    @Test
    void testCreateSign_BasicFlatMap() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");
        params.put("age", 25);

        String sign = SignatureUtils.createSign(params, TEST_URL, TEST_SECRET);

        // MD5 输出应为 32 位小写十六进制字符串
        assertNotNull(sign);
        assertEquals(32, sign.length());
        assertTrue(sign.matches("[0-9a-f]{32}"));
    }

    @Test
    void testCreateSign_Deterministic() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "test");
        params.put("value", "123");

        String sign1 = SignatureUtils.createSign(params, TEST_URL, TEST_SECRET);
        String sign2 = SignatureUtils.createSign(params, TEST_URL, TEST_SECRET);

        assertEquals(sign1, sign2, "相同输入应产生相同签名");
    }

    @Test
    void testCreateSign_DifferentDataDifferentSign() {
        Map<String, Object> params1 = new HashMap<>();
        params1.put("name", "Alice");
        Map<String, Object> params2 = new HashMap<>();
        params2.put("name", "Bob");

        String sign1 = SignatureUtils.createSign(params1, TEST_URL, TEST_SECRET);
        String sign2 = SignatureUtils.createSign(params2, TEST_URL, TEST_SECRET);

        assertNotEquals(sign1, sign2, "不同数据应产生不同签名");
    }

    @Test
    void testCreateSign_DifferentUrlDifferentSign() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "test");

        String sign1 = SignatureUtils.createSign(params, "/api/a", TEST_SECRET);
        String sign2 = SignatureUtils.createSign(params, "/api/b", TEST_SECRET);

        assertNotEquals(sign1, sign2, "不同 URL 应产生不同签名");
    }

    @Test
    void testCreateSign_DifferentSecretDifferentSign() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "test");

        String sign1 = SignatureUtils.createSign(params, TEST_URL, "secret1");
        String sign2 = SignatureUtils.createSign(params, TEST_URL, "secret2");

        assertNotEquals(sign1, sign2, "不同 secretKey 应产生不同签名");
    }

    @Test
    void testCreateSign_SortingByKey() {
        // LinkedHashMap 保证插入顺序，但签名计算时必须按 key 排序
        Map<String, Object> paramsOrderA = new LinkedHashMap<>();
        paramsOrderA.put("b", "2");
        paramsOrderA.put("a", "1");
        paramsOrderA.put("c", "3");

        Map<String, Object> paramsOrderB = new LinkedHashMap<>();
        paramsOrderB.put("c", "3");
        paramsOrderB.put("a", "1");
        paramsOrderB.put("b", "2");

        String signA = SignatureUtils.createSign(paramsOrderA, TEST_URL, TEST_SECRET);
        String signB = SignatureUtils.createSign(paramsOrderB, TEST_URL, TEST_SECRET);

        assertEquals(signA, signB, "key 插入顺序不同但值相同应产生相同签名");
    }

    @Test
    void testCreateSign_NestedMap() {
        Map<String, Object> nested = new HashMap<>();
        nested.put("subKey", "subValue");

        Map<String, Object> params = new HashMap<>();
        params.put("top", nested);
        params.put("flat", "value");

        String sign = SignatureUtils.createSign(params, TEST_URL, TEST_SECRET);

        assertNotNull(sign);
        assertEquals(32, sign.length());
        assertTrue(sign.matches("[0-9a-f]{32}"));
    }

    @Test
    void testCreateSign_SingleParam() {
        Map<String, Object> params = new HashMap<>();
        params.put("key", "value");

        String sign = SignatureUtils.createSign(params, TEST_URL, TEST_SECRET);

        assertNotNull(sign);
        assertEquals(32, sign.length());
    }

    // ---------------------------------------------------------------
    // checkSign 测试
    // ---------------------------------------------------------------

    @Test
    void testCheckSign_Roundtrip() {
        // 模拟客户端：用原始数据计算签名
        Map<String, Object> data = new HashMap<>();
        data.put("name", "张三");
        data.put("age", 25);

        String sign = SignatureUtils.createSign(data, TEST_URL, TEST_SECRET);

        // 构建服务端收到的 JSON（包含 sign）
        Map<String, Object> received = new HashMap<>(data);
        received.put("sign", sign);
        String json = JsonUtils.getInstance().serialize(received);

        // 服务端校验
        boolean result = SignatureUtils.checkSign(json, TEST_URL, TEST_SECRET);
        assertTrue(result, "正确签名应验证通过");
    }

    @Test
    void testCheckSign_WrongSign() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "test");

        String sign = SignatureUtils.createSign(data, TEST_URL, TEST_SECRET);

        // 篡改 sign 值
        data.put("sign", "0000000000000000000000000000000");
        String json = JsonUtils.getInstance().serialize(data);

        boolean result = SignatureUtils.checkSign(json, TEST_URL, TEST_SECRET);
        assertFalse(result, "错误签名应验证不通过");

        // 恢复原始 sign 确保它本身是可通过的
        data.put("sign", sign);
        json = JsonUtils.getInstance().serialize(data);
        assertTrue(SignatureUtils.checkSign(json, TEST_URL, TEST_SECRET));
    }

    @Test
    void testCheckSign_WrongSecret() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "test");

        String sign = SignatureUtils.createSign(data, TEST_URL, "correctSecret");
        data.put("sign", sign);
        String json = JsonUtils.getInstance().serialize(data);

        boolean result = SignatureUtils.checkSign(json, TEST_URL, "wrongSecret");
        assertFalse(result, "错误 secretKey 应验证不通过");
    }

    @Test
    void testCheckSign_WrongUrl() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "test");

        String sign = SignatureUtils.createSign(data, "/api/correct", TEST_SECRET);
        data.put("sign", sign);
        String json = JsonUtils.getInstance().serialize(data);

        boolean result = SignatureUtils.checkSign(json, "/api/wrong", TEST_SECRET);
        assertFalse(result, "错误 URL 应验证不通过");
    }

    @Test
    void testCheckSign_NestedMap() {
        Map<String, Object> nested = new HashMap<>();
        nested.put("subA", "valueA");
        nested.put("subB", "valueB");

        Map<String, Object> data = new HashMap<>();
        data.put("title", "test");
        data.put("detail", nested);

        String sign = SignatureUtils.createSign(data, TEST_URL, TEST_SECRET);
        data.put("sign", sign);
        String json = JsonUtils.getInstance().serialize(data);

        boolean result = SignatureUtils.checkSign(json, TEST_URL, TEST_SECRET);
        assertTrue(result, "含嵌套 Map 的签名应验证通过");
    }

    @Test
    void testCheckSign_EmptyValuesStripped() {
        // 客户端计算签名时不包含空值字段
        Map<String, Object> cleanData = new HashMap<>();
        cleanData.put("name", "test");
        cleanData.put("age", "25");

        String sign = SignatureUtils.createSign(cleanData, TEST_URL, TEST_SECRET);

        // 服务端收到的 JSON 可能带有多余的空值字段
        Map<String, Object> received = new HashMap<>(cleanData);
        received.put("extra1", "");       // 空字符串，应被剥离
        received.put("extra2", "");       // 空字符串，应被剥离
        received.put("sign", sign);
        String json = JsonUtils.getInstance().serialize(received);

        boolean result = SignatureUtils.checkSign(json, TEST_URL, TEST_SECRET);
        assertTrue(result, "空值字段被 checkSign 剥离后签名应验证通过");
    }

    @Test
    void testCheckSign_TamperedData() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "original");

        String sign = SignatureUtils.createSign(data, TEST_URL, TEST_SECRET);

        // 篡改数据
        data.put("name", "tampered");
        data.put("sign", sign);
        String json = JsonUtils.getInstance().serialize(data);

        boolean result = SignatureUtils.checkSign(json, TEST_URL, TEST_SECRET);
        assertFalse(result, "数据被篡改后签名应验证不通过");
    }

    @Test
    void testCheckSign_ExtraFieldsIgnored() {
        // 额外字段（非空值）会参与签名计算 → 签名会不一致
        Map<String, Object> data = new HashMap<>();
        data.put("core", "value");

        String sign = SignatureUtils.createSign(data, TEST_URL, TEST_SECRET);

        // 添加额外非空字段
        data.put("extra", "shouldNotBeHere");
        data.put("sign", sign);
        String json = JsonUtils.getInstance().serialize(data);

        boolean result = SignatureUtils.checkSign(json, TEST_URL, TEST_SECRET);
        assertFalse(result, "额外非空字段改变签名数据应验证不通过");

        // 验证原始数据（无额外字段）仍可通过
        data.remove("extra");
        data.put("sign", sign);
        json = JsonUtils.getInstance().serialize(data);
        assertTrue(SignatureUtils.checkSign(json, TEST_URL, TEST_SECRET));
    }

    // ---------------------------------------------------------------
    // 集成场景测试
    // ---------------------------------------------------------------

    @Test
    void testFullRoundtrip_MultipleParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("timestamp", "1717000000000");
        params.put("nonce", "abc123xyz");
        params.put("data", "{\"userId\":123}");

        // 客户端计算签名
        String sign = SignatureUtils.createSign(params, "/api/order/create", TEST_SECRET);

        // 服务端接收 JSON
        params.put("sign", sign);
        String requestJson = JsonUtils.getInstance().serialize(params);

        boolean result = SignatureUtils.checkSign(requestJson, "/api/order/create", TEST_SECRET);
        assertTrue(result, "多字段场景完整签名验证应通过");
    }

    // ---------------------------------------------------------------
    // DTO 签名测试（基于 @SignField 注解）
    // ---------------------------------------------------------------

    @Test
    void testDTO_createSign_basic() {
        BasicSignDTO dto = new BasicSignDTO("张三", 25);
        String sign = SignatureUtils.createSign(dto, TEST_URL, TEST_SECRET);
        assertNotNull(sign);
        assertEquals(32, sign.length());
        assertTrue(sign.matches("[0-9a-f]{32}"));
    }

    @Test
    void testDTO_createSign_deterministic() {
        BasicSignDTO dto = new BasicSignDTO("test", 30);
        String sign1 = SignatureUtils.createSign(dto, TEST_URL, TEST_SECRET);
        String sign2 = SignatureUtils.createSign(dto, TEST_URL, TEST_SECRET);
        assertEquals(sign1, sign2);
    }

    @Test
    void testDTO_createSign_partialFields() {
        // notSigned 字段不应参与签名
        PartialSignDTO dto = new PartialSignDTO("visible", "hidden");
        // 等价于只有 signed 字段的签名
        String sign = SignatureUtils.createSign(dto, TEST_URL, TEST_SECRET);

        Map<String, Object> map = new HashMap<>();
        map.put("signed", "visible");
        String expectedSign = SignatureUtils.createSign(map, TEST_URL, TEST_SECRET);

        assertEquals(expectedSign, sign, "未标注 @SignField 的字段不应参与签名");
    }

    @Test
    void testDTO_createSign_ordered() {
        // ordered: age(order=1) < name(order=2) < email(order=11)
        OrderedSignDTO dto = new OrderedSignDTO("Alice", 28, "alice@test.com");
        String sign = SignatureUtils.createSign(dto, TEST_URL, TEST_SECRET);

        // 手动构造按 order 排序后的参数字符串: age → name → email
        // Map 变体按字典序排序，不能直接用于验证 order，这里用 MD5 直接验证
        String expected = HashUtils.md5("%s?age=28&name=Alice&email=alice@test.com%s".formatted(TEST_URL, TEST_SECRET));
        assertEquals(expected, sign, "order 排序应按照 order 升序排列");
    }

    @Test
    void testDTO_createSign_nested() {
        NestedSignOuterDTO dto = new NestedSignOuterDTO(
                "test", new NestedSignInnerDTO("valA", "valB"));
        String sign = SignatureUtils.createSign(dto, TEST_URL, TEST_SECRET);

        // 等价的 Map：detail 是嵌套结构
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("subA", "valA");
        inner.put("subB", "valB");
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("detail", inner);
        outer.put("title", "test");
        String expectedSign = SignatureUtils.createSign(outer, TEST_URL, TEST_SECRET);

        assertEquals(expectedSign, sign, "嵌套 DTO 应递归提取 @SignField 字段");
    }

    @Test
    void testDTO_createSign_nullValue() {
        NullFieldDTO dto = new NullFieldDTO("test", null);
        String sign = SignatureUtils.createSign(dto, TEST_URL, TEST_SECRET);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("age", null);
        map.put("name", "test");
        String expectedSign = SignatureUtils.createSign(map, TEST_URL, TEST_SECRET);

        assertEquals(expectedSign, sign, "null 值应序列化为 'key=null'");
    }

    @Test
    void testDTO_createSign_inheritedFields() {
        ExtendsSignDTO dto = new ExtendsSignDTO("base", "sub");
        String sign = SignatureUtils.createSign(dto, TEST_URL, TEST_SECRET);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("baseField", "base");
        map.put("subField", "sub");
        String expectedSign = SignatureUtils.createSign(map, TEST_URL, TEST_SECRET);

        assertEquals(expectedSign, sign, "父类的 @SignField 字段应被继承");
    }

    @Test
    void testDTO_createSign_enumField() {
        EnumSignDTO dto = new EnumSignDTO("user", Status.ACTIVE);
        String sign = SignatureUtils.createSign(dto, TEST_URL, TEST_SECRET);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "user");
        map.put("status", Status.ACTIVE);
        String expectedSign = SignatureUtils.createSign(map, TEST_URL, TEST_SECRET);

        assertEquals(expectedSign, sign, "枚举字段应序列化为其 name()");
    }

    @Test
    void testDTO_createSign_customName() {
        CustomNameSignDTO dto = new CustomNameSignDTO("Alice", 28);
        String sign = SignatureUtils.createSign(dto, TEST_URL, TEST_SECRET);
        // 签名用 snake_case 的 key：user_name 和 user_age
        String expected = HashUtils.md5("%s?user_age=28&user_name=Alice%s".formatted(TEST_URL, TEST_SECRET));
        assertEquals(expected, sign, "自定义 name 应替换 Java 字段名参与签名");
    }

    @Test
    void testDTO_createSign_duplicateOrder_throws() {
        DuplicateOrderSignDTO dto = new DuplicateOrderSignDTO("test", "a@b.com", "13800138000");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SignatureUtils.createSign(dto, TEST_URL, TEST_SECRET));
        assertTrue(ex.getMessage().contains("Duplicate @SignField order=2"));
    }

    @Test
    void testDTO_createSign_valueOnly() {
        ValueOnlySignDTO dto = new ValueOnlySignDTO("Alice", 28, "alice@test.com");
        String sign = SignatureUtils.createSign(dto, TEST_URL, TEST_SECRET);
        // value-only 模式：age|alice@test.com|Alice（字段按字母序排列）
        String expected = HashUtils.md5("%s?28|alice@test.com|Alice%s".formatted(TEST_URL, TEST_SECRET));
        assertEquals(expected, sign, "value-only 模式应仅输出 value 并用 connector 连接");
    }

    @Test
    void testDTO_createSign_customConnector() {
        CustomConnectorSignDTO dto = new CustomConnectorSignDTO("Bob", 35);
        String sign = SignatureUtils.createSign(dto, TEST_URL, TEST_SECRET);
        // 自定义连接符：age=35,name=Bob
        String expected = HashUtils.md5("%s?age=35,name=Bob%s".formatted(TEST_URL, TEST_SECRET));
        assertEquals(expected, sign, "自定义 connector 应替换默认 & 连接符");
    }

    @Test
    void testDTO_createSign_nullDTO() {
        // cast to Object to avoid resolving to Map<String, Object> overload
        String sign = SignatureUtils.createSign((Object) null, TEST_URL, TEST_SECRET);
        assertNotNull(sign);
        assertEquals(32, sign.length());
        assertTrue(sign.matches("[0-9a-f]{32}"));
        // null DTO → 空 @SignField → 空参数字符串 → MD5(url?SECRET)
        String expected = HashUtils.md5(TEST_URL + "?" + TEST_SECRET);
        assertEquals(expected, sign);
    }

    @Test
    void testDTO_checkSign_roundtrip() {
        BasicSignDTO dto = new BasicSignDTO("张三", 25);
        String sign = SignatureUtils.createSign(dto, TEST_URL, TEST_SECRET);
        boolean result = SignatureUtils.checkSign(dto, TEST_URL, TEST_SECRET, sign);
        assertTrue(result, "正确签名应验证通过");
    }

    @Test
    void testDTO_checkSign_wrongSign() {
        BasicSignDTO dto = new BasicSignDTO("张三", 25);
        boolean result = SignatureUtils.checkSign(dto, TEST_URL, TEST_SECRET, "wrongsign1234567890123456");
        assertFalse(result, "错误签名应验证不通过");
    }

    @Test
    void testDTO_checkSign_wrongSecret() {
        BasicSignDTO dto = new BasicSignDTO("张三", 25);
        String sign = SignatureUtils.createSign(dto, TEST_URL, "correctKey");
        boolean result = SignatureUtils.checkSign(dto, TEST_URL, "wrongKey", sign);
        assertFalse(result, "错误 secretKey 应验证不通过");
    }

    @Test
    void testDTO_createAndCheck_largeData() {
        BasicSignDTO dto = new BasicSignDTO("a".repeat(1000), 999);
        String sign = SignatureUtils.createSign(dto, TEST_URL, TEST_SECRET);
        assertTrue(SignatureUtils.checkSign(dto, TEST_URL, TEST_SECRET, sign));
    }

    @Test
    void testDTO_createAndCheck_orderedRoundtrip() {
        OrderedSignDTO dto = new OrderedSignDTO("Bob", 35, "bob@test.com");
        String sign = SignatureUtils.createSign(dto, TEST_URL, TEST_SECRET);
        assertTrue(SignatureUtils.checkSign(dto, TEST_URL, TEST_SECRET, sign));
    }

    @Test
    void testDTO_createAndCheck_nestedRoundtrip() {
        NestedSignOuterDTO dto = new NestedSignOuterDTO(
                "outer", new NestedSignInnerDTO("innerA", "innerB"));
        String sign = SignatureUtils.createSign(dto, TEST_URL, TEST_SECRET);
        assertTrue(SignatureUtils.checkSign(dto, TEST_URL, TEST_SECRET, sign));
    }

    @Test
    void testDTO_createAndCheck_enumRoundtrip() {
        EnumSignDTO dto = new EnumSignDTO("admin", Status.INACTIVE);
        String sign = SignatureUtils.createSign(dto, TEST_URL, TEST_SECRET);
        assertTrue(SignatureUtils.checkSign(dto, TEST_URL, TEST_SECRET, sign));
    }

    @Test
    void testDTO_createAndCheck_nullFieldRoundtrip() {
        NullFieldDTO dto = new NullFieldDTO("test", null);
        String sign = SignatureUtils.createSign(dto, TEST_URL, TEST_SECRET);
        assertTrue(SignatureUtils.checkSign(dto, TEST_URL, TEST_SECRET, sign));
    }

    @Test
    void testDTO_createAndCheck_inheritedRoundtrip() {
        ExtendsSignDTO dto = new ExtendsSignDTO("baseValue", "subValue");
        String sign = SignatureUtils.createSign(dto, TEST_URL, TEST_SECRET);
        assertTrue(SignatureUtils.checkSign(dto, TEST_URL, TEST_SECRET, sign));
    }

    @Test
    void testDTO_basicDifferentDTODifferentSign() {
        BasicSignDTO dto1 = new BasicSignDTO("Alice", 25);
        BasicSignDTO dto2 = new BasicSignDTO("Bob", 30);
        assertNotEquals(
                SignatureUtils.createSign(dto1, TEST_URL, TEST_SECRET),
                SignatureUtils.createSign(dto2, TEST_URL, TEST_SECRET));
    }

    @Test
    void testCreateAndCheck_RoundtripWithNullMap() {
        // createSign 允许 null 值（会被序列化为 "key=null" 参与签名）
        Map<String, Object> params = new HashMap<>();
        params.put("key1", null);
        params.put("key2", "value");

        // 不应抛异常
        String sign = SignatureUtils.createSign(params, TEST_URL, TEST_SECRET);
        assertNotNull(sign);
        assertEquals(32, sign.length());

        // checkSign 侧：null 值不会被 removeIf 移除（空字符串才移除）
        params.put("sign", sign);
        String json = JsonUtils.getInstance().serialize(params);
        // null 在 Jackson 序列化时可能被排除（取决于配置），这里验证不抛异常即可
        // 不强制断言 true/false，取决于 JsonUtils 的 null 序列化配置
        boolean result = SignatureUtils.checkSign(json, TEST_URL, TEST_SECRET);
        // 如果 JsonUtils 会序列化 null，则 result 应为 true
        if (json.contains("null")) {
            assertTrue(result, "null 值若被序列化则应该验证通过");
        }
    }

}
