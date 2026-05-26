# C++ HTTP客户端库

C++版本的HTTP客户端库，支持ECC+AES-GCM加密和防重复提交功能。

## 📦 特性

- ✅ **ECC+AES-GCM加密** - 使用OpenSSL
- ✅ **防重复提交** - 客户端和服务器双重防护
- ✅ **灵活的配置** - 每个API端点独立配置安全选项
- ✅ **自动处理** - 密钥交换、重新握手、错误处理
- ✅ **URL接口分离** - 业务代码定义Url枚举，实现UrlInterface接口
- ✅ **跨平台** - 支持Windows、Linux、macOS
- ✅ **C++11标准** - 使用现代C++特性

## 🚀 快速开始

### 前置要求

- **C++11编译器** (GCC 4.7+, Clang 3.1+, MSVC 2012+)
- **CMake 3.10+**
- **libcurl 7.0+**
- **OpenSSL 1.1.0+**（推荐1.1.1+）

### 📦 依赖说明

#### 运行时依赖

本库需要以下系统库和工具：

| 依赖 | 用途 | 版本要求 |
|------|------|---------|
| **C++11编译器** | 编译源代码 | GCC 4.7+, Clang 3.1+, MSVC 2012+ |
| **libcurl** | HTTP客户端库 | 7.0+ |
| **OpenSSL** | ECC加密和MD5哈希 | 1.1.0+（推荐1.1.1+） |

#### 平台安装指南

**Ubuntu/Debian**:
```bash
sudo apt-get update
sudo apt-get install build-essential cmake
sudo apt-get install libcurl4-openssl-dev libssl-dev
```

**macOS**:
```bash
# 使用Homebrew
brew install cmake curl openssl
```

**Windows**:
```bash
# 使用vcpkg（推荐）
vcpkg install curl openssl:x64-windows

# 或使用预编译库
# 下载 libcurl: https://curl.se/windows/
# 下载 OpenSSL: https://slproweb.com/products/Win32OpenSSL.html
```

**CentOS/RHEL/Fedora**:
```bash
sudo yum install gcc-c++ cmake libcurl-devel openssl-devel
# 或
sudo dnf install gcc-c++ cmake libcurl-devel openssl-devel
```

### 安装

#### 方法1：直接复制文件

```bash
# 复制所有源文件到你的项目
cp -r cpp/* your-project/src/
```

#### 方法2：作为库集成

在CMakeLists.txt中添加：
```cmake
add_subdirectory(path/to/cpp)
target_link_libraries(your-target httpclient)
```

### 基本使用

```cpp
#include "http_client.h"
#include "url.h"

using namespace httpclient;

int main() {
    // 创建客户端
    HttpClientConfig config;
    config.baseUrl = "https://your-gateway.com";
    config.clientId = "cpp-client";
    
    HttpClient client(config);
    
    // 发送请求
    std::string loginData = R"({"username":"user","password":"pass"})";
    Response response = client.request(AppUrl::UserLogin, loginData);
    
    if (response.isSuccess()) {
        std::cout << "登录成功: " << response.body << std::endl;
    }
    
    return 0;
}
```

## 📚 详细使用

### 1. 定义URL配置（业务代码）

```cpp
// url.h
enum class AppUrl {
    UserLogin,
    OrderSubmit,
    MenuAll,
};

// 实现UrlInterface接口
class AppUrlHelper : public UrlInterface {
public:
    explicit AppUrlHelper(AppUrl url) : url_(url) {}
    
    std::string path() const override {
        switch (url_) {
        case AppUrl::UserLogin: return "/api/auth/login";
        case AppUrl::OrderSubmit: return "/api/order/submit";
        case AppUrl::MenuAll: return "/api/menu/all";
        }
    }
    
    Method method() const override {
        switch (url_) {
        case AppUrl::UserLogin:
        case AppUrl::OrderSubmit:
            return Method::POST;
        case AppUrl::MenuAll:
            return Method::GET;
        }
    }
    
    bool needEncryption() const override {
        switch (url_) {
        case AppUrl::UserLogin:
        case AppUrl::OrderSubmit:
            return true;
        case AppUrl::MenuAll:
            return false;
        }
    }
    
    bool needDuplicateCheck() const override {
        return url_ == AppUrl::OrderSubmit;
    }
    
private:
    AppUrl url_;
};
```

### 2. 创建客户端

```cpp
HttpClientConfig config;
config.baseUrl = "https://your-gateway.com";
config.clientId = "cpp-client";
config.duplicateSubmitTimeWindow = 3000;
config.timeout = 30000;

HttpClient client(config);
```

### 3. 发送请求

```cpp
// 基本请求（无需加密，无需防重复）
std::string menuData = "";
Response response = client.request(AppUrl::MenuAll, menuData);

// 加密请求（登录）
std::string loginData = R"({"username":"user","password":"pass"})";
Response loginResponse = client.request(AppUrl::UserLogin, loginData);

// 加密+防重复请求（订单提交）
std::string orderData = R"({
    "items": [{"productId": "P001", "quantity": 2}],
    "totalAmount": 199.98
})";
Response orderResponse = client.request(AppUrl::OrderSubmit, orderData);
```

### 4. 处理响应

```cpp
Response response = client.request(AppUrl::UserLogin, loginData);

if (response.isSuccess()) {
    // 解析JSON响应
    std::cout << "请求成功: " << response.body << std::endl;
} else if (response.statusCode == 429) {
    // 服务器检测到重复提交
    std::cout << "检测到重复提交" << std::endl;
} else {
    // 其他错误
    std::cout << "请求失败: " << response.statusCode 
              << ", " << response.body << std::endl;
}
```

### 5. 异步请求

```cpp
client.requestAsync(AppUrl::UserProfile, "", [](const Response& resp) {
    if (resp.isSuccess()) {
        std::cout << "获取用户信息成功: " << resp.body << std::endl;
    }
});

// 等待异步请求完成
std::this_thread::sleep_for(std::chrono::seconds(2));
```

## 🔐 加密说明

C++版本使用OpenSSL库：

1. **ECC密钥交换**: `EC_KEY` + `ECDH_compute_key`
2. **共享密钥**: ECDH计算得到的256位密钥
3. **AES-256-GCM加密**: `EVP_*` 系列函数

所有加密功能都使用OpenSSL库，需要在编译和运行时链接OpenSSL。

## ⚠️ 注意事项

1. **C++标准**: 需要C++11或更高版本
2. **线程安全**: HttpClient实例不是线程安全的，多线程使用时需要加锁
3. **内存管理**: 使用智能指针管理资源，确保正确清理
4. **错误处理**: 始终检查Response的状态码和isSuccess()
5. **依赖版本**: OpenSSL需要1.1.0+，推荐使用1.1.1+以获得最佳兼容性

## 🔨 编译

### 使用CMake

```bash
mkdir build && cd build
cmake ..
make
```

如果CMake找不到依赖，可以手动指定路径：

```bash
cmake .. -DOPENSSL_ROOT_DIR=/path/to/openssl \
         -DCURL_ROOT_DIR=/path/to/curl
```

### 验证依赖

编译前验证依赖是否正确安装：

```bash
# 检查OpenSSL
openssl version
openssl ecparam -list_curves | grep prime256v1

# 检查libcurl
curl-config --version
curl-config --features | grep SSL
```

## 📖 示例代码

查看 `example/` 目录获取完整示例：

- `url.h` - URL配置示例
- `main.cpp` - 完整使用示例

---

**版本**: 1.0  
**更新**: 2025-11-01  
**C++标准**: C++11
