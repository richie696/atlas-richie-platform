# Richie HTTP Component

基于 OkHttp 和 HttpClient5 的统一 HTTP 客户端组件，提供简洁易用的 API，支持同步/异步请求、文件上传下载、SOAP 协议、自定义超时配置等功能。

## 📋 目录

- [功能特性](#功能特性)
- [快速开始](#快速开始)
- [核心功能](#核心功能)
- [配置说明](#配置说明)
- [最佳实践](#最佳实践)
- [常见问题](#常见问题)

---

## ✨ 功能特性

### 核心能力

- ✅ **双引擎支持**：支持 OkHttp 和 HttpClient5 两种实现，可灵活切换
- ✅ **统一 API**：提供统一的 `HttpClientApi` 接口，屏蔽底层实现差异
- ✅ **同步/异步请求**：支持同步阻塞和异步非阻塞两种请求方式
- ✅ **多种请求方法**：支持 GET、POST、DELETE 等 HTTP 方法
- ✅ **自动序列化**：自动将请求对象序列化为 JSON，响应自动反序列化为对象
- ✅ **文件上传下载**：支持单文件/批量文件上传，支持文件流下载
- ✅ **SOAP 支持**：支持 SOAP1.2 协议请求
- ✅ **灵活配置**：支持自定义超时时间、连接池、日志级别等

### 高级特性

- ✅ **连接池管理**：自动管理连接池，提升性能
- ✅ **请求日志**：支持请求/响应日志记录，便于调试
- ✅ **SSL/TLS 支持**：支持自定义证书、信任库配置
- ✅ **动态线程池**：集成 Dynamic-TP，支持动态线程池管理
- ✅ **错误处理**：完善的错误处理和异常回调机制

---

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-http</artifactId>
</dependency>
```

### 2. 配置 HTTP 客户端

```yaml
# application.yml
platform:
  component:
    http:
      provider: okhttp  # 或 http_client_5
      # 超时配置
      read-timeout: 5                    # 读取超时（秒）
      write-timeout: 5                   # 写入超时（秒）
      connect-timeout: 5                 # 连接超时（秒）
      call-timeout: 15                   # 调用超时（秒）
      # 日志配置
      level: BODY                        # 日志级别：NONE、BASIC、HEADERS、BODY
      # 连接池配置
      max-requests: 250                  # 最大并发请求数
      max-requests-per-host: 25          # 每个主机最大并发请求数
      keep-alive-duration: 5             # 连接保持时间（分钟）
      # 缓存配置（可选）
      enable-cache: false                # 是否启用HTTP缓存
      cache-path: /opt/okhttp3/cache/    # 缓存路径
      cache-size: 100                    # 缓存大小（MB）
      # SSL配置（可选）
      insecure-trust-all: false          # 是否跳过所有证书校验（仅测试用）
      hostname-verification: true         # 是否启用主机名校验
```

### 3. 注入 HttpClientApi

```java
import client.com.richie.component.http.HttpClientApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ApiService {

    @Autowired
    private HttpClientApi httpClient;

    // 使用 httpClient 进行HTTP请求
}
```

### 4. 使用示例

```java
@Service
public class UserApiService {
    
    @Autowired
    private HttpClientApi httpClient;
    
    /**
     * GET 请求（返回字符串）
     */
    public String getUserInfo(String userId) {
        String url = "https://api.example.com/users/" + userId;
        return httpClient.doGet(url);
    }
    
    /**
     * GET 请求（返回对象）
     */
    public UserInfo getUserInfoObject(String userId) {
        String url = "https://api.example.com/users/" + userId;
        return httpClient.doGet(UserInfo.class, url);
    }
    
    /**
     * POST 请求（对象参数）
     */
    public ApiResponse createUser(CreateUserRequest request) {
        String url = "https://api.example.com/users";
        return httpClient.doPost(ApiResponse.class, url, request);
    }
    
    /**
     * POST 请求（带请求头）
     */
    public ApiResponse createUserWithHeader(CreateUserRequest request) {
        String url = "https://api.example.com/users";
        Map<String, String> headers = Map.of(
            "Authorization", "Bearer token123",
            "X-Request-Id", UUID.randomUUID().toString()
        );
        return httpClient.doPost(ApiResponse.class, url, request, headers);
    }
}
```

---

## 🔧 核心功能

### 1. GET 请求

#### 简单 GET 请求

```java
// 返回字符串
String response = httpClient.doGet("https://api.example.com/data");

// 返回对象
UserInfo user = httpClient.doGet(UserInfo.class, "https://api.example.com/users/123");
```

#### 带参数 GET 请求

```java
Map<String, String> params = Map.of(
    "page", "1",
    "size", "10",
    "keyword", "test"
);
String response = httpClient.doGet("https://api.example.com/search", params);

// 返回对象
List<UserInfo> users = httpClient.doGet(
    new TypeReference<List<UserInfo>>(){},
    "https://api.example.com/users",
    params
);
```

#### 带请求头 GET 请求

```java
Map<String, String> headers = Map.of("Authorization", "Bearer token123");
String response = httpClient.doGet(
    "https://api.example.com/data",
    params,
    headers
);
```

#### 错误处理

```java
List<Throwable> errors = new ArrayList<>();
String response = httpClient.doGet(
    "https://api.example.com/data",
    params,
    headers,
    errors
);
if (!errors.isEmpty()) {
    // 处理错误
    errors.forEach(e -> log.error("请求失败", e));
}
```

### 2. POST 请求

#### JSON POST 请求

```java
// 对象参数，自动序列化为JSON
CreateUserRequest request = new CreateUserRequest("test", "test@example.com");
ApiResponse response = httpClient.doPost(ApiResponse.class, url, request);

// 字符串参数
String json = "{\"username\":\"test\",\"email\":\"test@example.com\"}";
String response = httpClient.doPost(url, json);
```

#### 表单 POST 请求

```java
Map<String, String> params = Map.of("username", "test", "email", "test@example.com");
String response = httpClient.doPostFormBody(String.class, url, params);
```

#### 自定义 Content-Type

```java
String response = httpClient.doPost(
    url,
    request,
    headers,
    "application/xml"  // 自定义Content-Type
);
```

### 3. DELETE 请求

```java
Map<String, String> headers = Map.of("Authorization", "Bearer token123");
String json = "{\"reason\":\"deleted by admin\"}";
String response = httpClient.doDelete(url, json, headers);
```

### 4. 异步请求

#### 异步 GET 请求

```java
httpClient.doAsyncGet(UserInfo.class, url, new AsyncCallback<UserInfo>() {
    @Override
    public void onResponse(HttpResponse response, UserInfo data) {
        // 处理成功响应
        log.info("获取用户信息成功: {}", data);
    }
    
    @Override
    public void onFailure(IOException exception) {
        // 处理失败
        log.error("获取用户信息失败", exception);
    }
});
```

#### 异步 POST 请求

```java
CreateUserRequest request = new CreateUserRequest("test", "test@example.com");
httpClient.doAsyncPost(ApiResponse.class, url, request, new AsyncCallback<ApiResponse>() {
    @Override
    public void onResponse(HttpResponse response, ApiResponse data) {
        log.info("创建用户成功: {}", data);
    }
    
    @Override
    public void onFailure(IOException exception) {
        log.error("创建用户失败", exception);
    }
});
```

#### 异步请求（带超时）

```java
httpClient.doAsyncPost(
    ApiResponse.class,
    url,
    request,
    headers,
    30000L,  // 30秒超时
    new AsyncCallback<ApiResponse>() {
        // ...
    }
);
```

### 5. 文件上传

#### 单文件上传

```java
import client.com.richie.component.http.HttpClientApi.UploadFileMetadata;

InputStream fileStream = new FileInputStream("test.jpg");
UploadFileMetadata metadata = new UploadFileMetadata(
        "file",           // 参数名
        "test.jpg",       // 文件名
        fileStream        // 文件流
);

Map<String, String> params = Map.of("description", "测试图片");
Map<String, String> headers = Map.of("Authorization", "Bearer token123");

String response = httpClient.doUploadInputStream(url, metadata, params, headers);
```

#### 批量文件上传

```java
List<UploadFileMetadata> files = List.of(
    new UploadFileMetadata("file1", "test1.jpg", stream1),
    new UploadFileMetadata("file2", "test2.jpg", stream2)
);

String response = httpClient.doUploadInputStream(url, files, params, headers);
```

#### 文件对象上传

```java
Map<String, File> fileMap = Map.of("file", new File("test.jpg"));
String response = httpClient.doUploadFile(url, fileMap, params, headers);
```

### 6. 文件下载

#### 下载到文件

```java
String destPath = "/tmp/downloaded_file.jpg";
File file = httpClient.doDownloadFile(url, destPath);
```

#### 下载为流

```java
InputStream stream = httpClient.doDownloadFile(url);
// 处理流
try (stream) {
    // 读取文件内容
}
```

#### POST 方式下载

```java
DownloadRequest request = new DownloadRequest("fileId", "123");
InputStream stream = httpClient.doDownloadFileByPost(url, request);
```

#### 异步下载

```java
httpClient.doAsyncDownloadFile(url, new AsyncCallback<InputStream>() {
    @Override
    public void onResponse(HttpResponse response, InputStream data) {
        try (data) {
            // 处理文件流
        }
    }
    
    @Override
    public void onFailure(IOException exception) {
        log.error("下载失败", exception);
    }
});
```

### 7. SOAP 请求

```java
String soapXml = """
    <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
        <soap:Body>
            <GetUserInfo>
                <userId>123</userId>
            </GetUserInfo>
        </soap:Body>
    </soap:Envelope>
    """;

// 同步SOAP请求
String response = httpClient.doPostSOAP1_2(url, soapXml);

// 返回对象
SoapResponse response = httpClient.doPostSOAP1_2(SoapResponse.class, url, soapXml);

// 异步SOAP请求
httpClient.doAsyncPostSOAP1_2(SoapResponse.class, url, soapXml, new AsyncCallback<SoapResponse>() {
    // ...
});
```

### 8. XML 请求

```java
String xml = "<request><userId>123</userId></request>";
String response = httpClient.doPostXml(url, xml);

// 返回对象
XmlResponse response = httpClient.doPostXml(XmlResponse.class, url, xml);
```

---

## ⚙️ 配置说明

### 基础配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `platform.component.http.provider` | String | `okhttp` | HTTP客户端提供商：`okhttp`、`http_client_5` |
| `platform.component.http.read-timeout` | Integer | `5` | 读取超时时间（秒） |
| `platform.component.http.write-timeout` | Integer | `5` | 写入超时时间（秒） |
| `platform.component.http.connect-timeout` | Integer | `5` | 连接超时时间（秒） |
| `platform.component.http.call-timeout` | Integer | `15` | 调用超时时间（秒） |

### 日志配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `platform.component.http.level` | String | `BODY` | 日志级别：`NONE`、`BASIC`、`HEADERS`、`BODY` |

### 连接池配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `platform.component.http.max-requests` | Integer | `250` | 整体实例最大并发请求数 |
| `platform.component.http.max-requests-per-host` | Integer | `25` | 同一主机最大并发请求数 |
| `platform.component.http.keep-alive-duration` | Long | `5` | 连接保持时间（分钟） |

### 缓存配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `platform.component.http.enable-cache` | Boolean | `false` | 是否启用HTTP缓存 |
| `platform.component.http.cache-path` | String | `/opt/okhttp3/cache/` | 缓存路径 |
| `platform.component.http.cache-size` | Integer | `100` | 缓存大小（MB） |

### SSL/TLS 配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `platform.component.http.insecure-trust-all` | Boolean | `false` | 是否跳过所有证书校验（仅测试用） |
| `platform.component.http.hostname-verification` | Boolean | `true` | 是否启用主机名校验 |
| `platform.component.http.truststore-path` | String | - | 自定义信任库路径（JKS/PKCS12） |
| `platform.component.http.truststore-password` | String | - | 自定义信任库密码 |
| `platform.component.http.truststore-type` | String | `JKS` | 信任库类型：`JKS`、`PKCS12` |

---

## 🎯 最佳实践

### 1. 选择合适的 HTTP 客户端

- **OkHttp**（默认）：适合大多数场景，性能优秀，API 简洁
- **HttpClient5**：适合需要更细粒度控制的场景

### 2. 超时配置

```yaml
platform:
  component:
    http:
      connect-timeout: 5      # 连接超时：5秒
      read-timeout: 10        # 读取超时：10秒（根据接口响应时间调整）
      write-timeout: 10       # 写入超时：10秒
      call-timeout: 30        # 调用超时：30秒（整体超时）
```

### 3. 连接池配置

```yaml
platform:
  component:
    http:
      max-requests: 250              # 根据服务器性能调整
      max-requests-per-host: 25      # 根据目标服务器限制调整
      keep-alive-duration: 5         # 连接保持时间
```

### 4. 错误处理

```java
// 方式一：使用错误列表
List<Throwable> errors = new ArrayList<>();
String response = httpClient.doGet(url, params, headers, errors);
if (!errors.isEmpty()) {
    errors.forEach(e -> log.error("请求失败", e));
    throw new RuntimeException("HTTP请求失败");
}

// 方式二：使用异步回调
httpClient.doAsyncGet(UserInfo.class, url, new AsyncCallback<UserInfo>() {
    @Override
    public void onResponse(HttpResponse response, UserInfo data) {
        // 处理成功
    }
    
    @Override
    public void onFailure(IOException exception) {
        // 处理失败，记录日志、发送告警等
        log.error("请求失败", exception);
    }
});
```

### 5. 请求头管理

```java
// 统一请求头管理
public class ApiHeaders {
    public static Map<String, String> defaultHeaders() {
        return Map.of(
            "Content-Type", "application/json",
            "Accept", "application/json",
            "User-Agent", "Richie-Client/1.0"
        );
    }
    
    public static Map<String, String> withAuth(String token) {
        Map<String, String> headers = new HashMap<>(defaultHeaders());
        headers.put("Authorization", "Bearer " + token);
        return headers;
    }
}

// 使用
Map<String, String> headers = ApiHeaders.withAuth(token);
String response = httpClient.doGet(url, params, headers);
```

### 6. 文件上传优化

```java
// 大文件上传建议使用异步方式
httpClient.doAsyncUploadFile(
    url,
    fileMap,
    params,
    headers,
    60000L,  // 60秒超时
    new AsyncCallback<String>() {
        @Override
        public void onResponse(HttpResponse response, String data) {
            log.info("文件上传成功: {}", data);
        }
        
        @Override
        public void onFailure(IOException exception) {
            log.error("文件上传失败", exception);
        }
    }
);
```

### 7. 日志级别选择

```yaml
# 开发环境：详细日志
platform:
  component:
    http:
      level: BODY  # 记录请求和响应体

# 生产环境：基础日志
platform:
  component:
    http:
      level: BASIC  # 只记录请求方法和URL
```

---

## ❓ 常见问题

### Q1: 如何切换 HTTP 客户端实现？

**A:** 在配置中设置 `provider`：

```yaml
platform:
  component:
    http:
      provider: http_client_5  # 或 okhttp
```

### Q2: 如何处理请求超时？

**A:** 
- 全局超时：在配置中设置 `read-timeout`、`write-timeout`、`connect-timeout`
- 单次请求超时：使用带 `timeout` 参数的方法（如 `doAsyncPost`）

### Q3: 如何自定义 SSL 证书？

**A:** 在配置中设置信任库：

```yaml
platform:
  component:
    http:
      truststore-path: /path/to/truststore.jks
      truststore-password: password
      truststore-type: JKS
```

### Q4: 异步请求的回调在哪个线程执行？

**A:** 异步请求的回调在 OkHttp/HttpClient5 的线程池中执行，注意线程安全。

### Q5: 如何上传大文件？

**A:** 建议使用异步上传方式，并设置合适的超时时间：

```java
httpClient.doAsyncUploadFile(url, fileMap, params, headers, 300000L, callback);
```

### Q6: 如何获取响应状态码和响应头？

**A:** 使用 `HttpResponse` 对象：

```java
// 异步请求中
@Override
public void onResponse(HttpResponse response, UserInfo data) {
    int statusCode = response.getCode();
    HttpHeader headers = response.getHeaders();
    String contentType = response.getContentType();
}
```

### Q7: 如何禁用请求日志？

**A:** 设置日志级别为 `NONE`：

```yaml
platform:
  component:
    http:
      level: NONE
```

---

## 📝 总结

Richie HTTP Component 提供了统一、易用的 HTTP 客户端解决方案，支持多种请求方式、文件操作、异步处理等特性。通过合理配置和使用，可以构建高性能、高可用的 HTTP 客户端应用。

**关键要点**：

1. **选择合适的客户端**：根据场景选择 OkHttp 或 HttpClient5
2. **合理配置超时**：根据接口响应时间设置合适的超时时间
3. **优化连接池**：根据并发需求调整连接池大小
4. **错误处理**：完善的错误处理和日志记录
5. **异步处理**：对于耗时操作使用异步请求，提升性能

