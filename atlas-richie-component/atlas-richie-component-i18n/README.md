# Richie I18n Component

基于 Spring MessageSource 的国际化组件，提供资源文件国际化、字典国际化、自动注入等能力，支持多语言切换和动态字典管理。

## 📋 目录

- [功能特性](#功能特性)
- [快速开始](#快速开始)
- [核心功能](#核心功能)
  - [Locale 获取流程](#4-locale-获取流程)
  - [自定义区域解析](#5-自定义区域解析)
- [配置说明](#配置说明)
- [最佳实践](#最佳实践)
- [常见问题](#常见问题)

---

## ✨ 功能特性

### 核心能力

- ✅ **资源文件国际化**：基于 Spring MessageSource，支持 properties 文件国际化
- ✅ **字典国际化**：支持动态字典国际化，通过 Redis 缓存提升性能
- ✅ **自动注入**：通过切面自动注入字典值，无需手动处理
- ✅ **静态工具类**：提供 `I18n` 静态工具类，使用便捷
- ✅ **多语言支持**：支持自定义区域（Locale），灵活切换语言
- ✅ **性能优化**：字典数据缓存到 Redis，默认保留7天

### 高级特性

- ✅ **切面控制**：支持通过 `@I18nControl` 注解控制切面执行
- ✅ **字段注解**：通过 `@I18nDict` 注解标记需要国际化的字段
- ✅ **动态管理**：支持动态添加、删除、查询国际化字典
- ✅ **类型安全**：支持类型安全的国际化解析
- ✅ **智能 Locale 解析**：遵循明确的优先级规则（请求头 → 配置默认值 → 请求 Locale），支持 Web 和非 Web 环境

---

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-i18n</artifactId>
</dependency>

<!-- 需要依赖 cache 组件（用于字典缓存） -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-cache</artifactId>
</dependency>
```

### 2. 配置国际化

```yaml
# application.yml
platform:
  component:
    i18n:
      # 国际化资源文件路径
      path: i18n/messages
      # 资源文件编码
      encoding: UTF-8
      # 默认区域（语言）
      default-locale: zh_CN
      # 是否启用切面控制（推荐：true）
      enable-i18n-control: true
      # 是否启用切面国际化（需要配合 @I18nControl 使用）
      enable-aspect-i18n: true
```

### 3. 创建国际化资源文件

```properties
# src/main/resources/i18n/messages_zh_CN.properties
user.name=用户名
user.email=邮箱
user.created=用户创建成功
user.notfound=用户不存在：{0}

# src/main/resources/i18n/messages_en_US.properties
user.name=Username
user.email=Email
user.created=User created successfully
user.notfound=User not found: {0}
```

### 4. 使用国际化

```java
import resolver.com.richie.component.i18n.I18n;

@Service
public class UserService {

    public void createUser(User user) {
        // 使用静态工具类获取国际化文本
        String message = I18n.get("user.created");
        log.info(message);
    }

    public void getUser(String userId) {
        // 带参数的国际化文本
        String message = I18n.get("user.notfound", userId);
        throw new RuntimeException(message);
    }

    // 指定语言
    public String getMessage(String key) {
        return I18n.get(Locale.US, key);  // 获取英文文本
    }
}
```

---

## 🔧 核心功能

### 1. 资源文件国际化

#### 创建资源文件

在 `src/main/resources/i18n/` 目录下创建资源文件：

```properties
# messages_zh_CN.properties（中文）
welcome.message=欢迎使用系统
user.name=用户名
user.age=年龄
user.created=用户 {0} 创建成功，邮箱：{1}

# messages_en_US.properties（英文）
welcome.message=Welcome to the system
user.name=Username
user.age=Age
user.created=User {0} created successfully, email: {1}
```

#### 使用静态工具类

```java
import resolver.com.richie.component.i18n.I18n;

// 使用默认区域
String message = I18n.get("welcome.message");

        // 带参数
        String message = I18n.get("user.created", "张三", "zhangsan@example.com");

        // 指定区域
        String message = I18n.get(Locale.US, "welcome.message");
```

#### 使用 I18nResolver

```java
import resolver.com.richie.component.i18n.I18nResolver;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class UserService {

    @Autowired
    private I18nResolver i18nResolver;

    public String getMessage(String key, Object... args) {
        return i18nResolver.get(key, args);
    }

    public String getMessage(Locale locale, String key, Object... args) {
        return i18nResolver.get(locale, key, args);
    }
}
```

### 2. 字典国际化

字典国际化适用于需要从数据库或外部系统动态获取的国际化数据。

#### 注册字典

```java


// 注册单个字典
Map<String, String> statusDict = Map.of(
        "zh_CN", "已启用",
        "en_US", "Enabled",
        "ja_JP", "有効"
);
I18nHandle.

        addI18nDictionary("user.status",statusDict);

        // 批量注册字典
        Map<String, Map<String, String>> dictMap = Map.of(
                "user.status", Map.of(
                        "zh_CN", "已启用",
                        "en_US", "Enabled"
                ),
                "order.status", Map.of(
                        "zh_CN", "已完成",
                        "en_US", "Completed"
                )
        );
I18nHandle.

        addI18nDictionaries(dictMap);
```

#### 查询字典

```java
// 获取完整字典
Map<String, String> dict = I18nHandle.getI18nDictionaries("user.status");
// 返回：{zh_CN=已启用, en_US=Enabled, ja_JP=有効}

// 获取指定语言的字典值
String value = I18nHandle.getI18nDictionary("user.status", "zh_CN");
// 返回：已启用

// 如果字典不存在，返回key本身
String value = I18nHandle.getI18nDictionary("not.exist", "zh_CN");
// 返回：not.exist
```

#### 删除字典

```java
I18nHandle.deleteI18nDictionary("user.status");
```

### 3. 自动注入字典（切面）

组件提供了切面功能，可以自动为 Controller 返回的数据注入字典值。

#### 启用切面

```yaml
platform:
  component:
    i18n:
      enable-aspect-i18n: true      # 启用切面国际化
      enable-i18n-control: true     # 启用切面控制（推荐）
```

#### 标记需要国际化的字段

```java
import annotation.com.richie.component.i18n.I18nDict;

@Data
public class UserVO {
    private Long id;
    private String username;

    @I18nDict  // 标记此字段需要国际化
    private String status;  // 值为字典key，如 "user.status"
}
```

#### 在 Controller 上使用注解

```java
import annotation.com.richie.component.i18n.I18nControl;

@RestController
@I18nControl  // 标记此Controller需要国际化处理
public class UserController {

    @GetMapping("/users/{id}")
    public ResultVO<UserVO> getUser(@PathVariable Long id) {
        UserVO user = userService.getById(id);
        user.setStatus("user.status");  // 设置字典key
        return ResultVO.success(user);
    }

    @GetMapping("/users")
    public ResultVO<Page<UserVO>> getUsers() {
        Page<UserVO> page = userService.page();
        // 自动为 page.getRecords() 中的每个 UserVO 注入字典值
        return ResultVO.success(page);
    }
}
```

**工作原理**：
1. 切面拦截 Controller 方法
2. 如果返回 `ResultVO` 且包含数据，提取数据中的 `@I18nDict` 字段
3. 根据当前语言环境，从 Redis 缓存中获取对应的字典值
4. 将字典值注入到 `ResultVO.i18nDict` 中，前端可以根据字典key获取对应的国际化文本

**返回结果示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "test",
    "status": "user.status"  // 字典key
  },
  "i18nDict": {  // 自动注入的字典
    "user.status": {
      "zh_CN": "已启用",
      "en_US": "Enabled",
      "ja_JP": "有効"
    }
  }
}
```

### 4. Locale 获取流程

组件使用 `LocaleContextHolder.getLocale()` 获取当前区域，Locale 的解析遵循以下优先级：

#### Locale 解析优先级

```
1. 请求头 X_RD_REQUEST_LANGUAGE（如果存在且有效）
   ↓ (为空或解析失败)
2. 配置的默认 Locale（platform.component.i18n.default-locale，默认：zh_CN）
   ↓ (如果为 null)
3. 请求的 Locale（request.getLocale()，来自 Accept-Language 头）
```

#### 详细流程说明

**Web 环境（HTTP 请求）**：

1. **请求头解析**：
   - 组件优先从请求头 `X_RD_REQUEST_LANGUAGE` 获取语言信息
   - 如果请求头存在且格式正确，解析并返回对应的 Locale
   - 请求头格式示例：`zh-CN`、`en-US`、`zh-CN,zh;q=0.9,en;q=0.8`

2. **回退到配置的默认 Locale**：
   - 如果请求头为空或解析失败，使用配置的 `defaultLocale`（默认：`Locale.CHINA`）
   - 这确保了在没有请求头的情况下，也能使用预期的默认语言

3. **回退到请求的 Locale**：
   - 只有在配置的 `defaultLocale` 为 null 时，才使用 `request.getLocale()`
   - `request.getLocale()` 来自浏览器的 `Accept-Language` 头

**非 Web 环境（定时任务、消息队列等）**：

- 直接使用配置的 `defaultLocale`
- 如果 `LocaleContextHolder.getLocale()` 返回 null，自动回退到 `defaultLocale`

#### 配置示例

```yaml
platform:
  component:
    i18n:
      # 设置默认区域（当请求头为空时使用）
      default-locale: zh_CN
```

#### 代码示例

```java
// 组件内部自动处理 Locale 获取和回退
String message = I18n.get("welcome.message");
// 1. 尝试从 LocaleContextHolder 获取 Locale（由 Spring MVC 自动设置）
// 2. 如果为 null，使用配置的 defaultLocale
// 3. 使用获取到的 Locale 查找对应的资源文件

// 手动指定 Locale（不依赖请求上下文）
String message = I18n.get(Locale.US, "welcome.message");
```

### 5. 自定义区域解析

如果需要自定义 Locale 解析逻辑，可以通过实现 `LocaleResolver` 来自定义：

```java
@Component
public class CustomLocaleResolver implements LocaleResolver {
    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        // 自定义解析逻辑
        String lang = request.getHeader("X-Custom-Language");
        if (lang != null) {
            return Locale.forLanguageTag(lang);
        }
        // 回退到配置的默认 Locale
        return Locale.CHINA;  // 或从配置中获取
    }
    
    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        // 设置区域
    }
}
```

**注意**：自定义 `LocaleResolver` 时，建议遵循相同的优先级规则，确保在没有请求头时回退到配置的默认 Locale。

---

## ⚙️ 配置说明

### 基础配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `platform.component.i18n.path` | String | `i18n/messages` | 国际化资源文件路径（相对于 classpath） |
| `platform.component.i18n.encoding` | String | `UTF-8` | 资源文件编码 |
| `platform.component.i18n.default-locale` | String | `zh_CN` | 默认区域（语言） |
| `platform.component.i18n.enable-i18n-control` | Boolean | `false` | 是否启用切面控制（推荐：true） |
| `platform.component.i18n.enable-aspect-i18n` | Boolean | `false` | 是否启用切面国际化（需要配合 @I18nControl 使用） |

---

## 🎯 最佳实践

### 1. 资源文件组织

```
src/main/resources/
  └── i18n/
      ├── messages_zh_CN.properties    # 中文
      ├── messages_en_US.properties      # 英文
      ├── messages_ja_JP.properties      # 日文
      └── messages.properties            # 默认（可选）
```

### 2. 资源文件命名规范

- 使用有意义的 key：`user.name`、`order.status`、`error.notfound`
- 使用点号分隔层级：`user.profile.name`、`order.payment.status`
- 避免使用特殊字符：只使用字母、数字、点号、下划线

### 3. 字典管理

```java
@Service
public class DictService {
    
    /**
     * 初始化字典数据（应用启动时调用）
     */
    @PostConstruct
    public void initDicts() {
        // 从数据库加载字典
        List<Dict> dicts = dictMapper.selectList(null);
        
        Map<String, Map<String, String>> dictMap = new HashMap<>();
        for (Dict dict : dicts) {
            Map<String, String> localeMap = Map.of(
                "zh_CN", dict.getZhCn(),
                "en_US", dict.getEnUs(),
                "ja_JP", dict.getJaJp()
            );
            dictMap.put(dict.getDictKey(), localeMap);
        }
        
        // 批量注册字典
        I18nHandle.addI18nDictionaries(dictMap);
    }
    
    /**
     * 更新字典（字典变更时调用）
     */
    public void updateDict(String dictKey, Map<String, String> localeMap) {
        I18nHandle.addI18nDictionary(dictKey, localeMap);
    }
    
    /**
     * 删除字典
     */
    public void deleteDict(String dictKey) {
        I18nHandle.deleteI18nDictionary(dictKey);
    }
}
```

### 4. Controller 使用

```java
@RestController
@I18nControl  // 标记需要国际化处理
public class UserController {
    
    @GetMapping("/users/{id}")
    public ResultVO<UserVO> getUser(@PathVariable Long id) {
        UserVO user = userService.getById(id);
        // 设置字典key，切面会自动注入字典值
        user.setStatus("user.status");
        user.setType("user.type");
        return ResultVO.success(user);
    }
    
    @GetMapping("/users")
    public ResultVO<Page<UserVO>> getUsers() {
        Page<UserVO> page = userService.page();
        // 自动为分页数据中的每个对象注入字典值
        return ResultVO.success(page);
    }
}
```

### 5. 前端使用

```javascript
// 从 i18nDict 中获取国际化文本
function getI18nText(i18nDict, key, locale = 'zh_CN') {
    if (!i18nDict || !i18nDict[key]) {
        return key;
    }
    return i18nDict[key][locale] || i18nDict[key]['zh_CN'] || key;
}

// 使用示例
const user = response.data;
const statusText = getI18nText(response.i18nDict, user.status, 'zh_CN');
```

### 6. 性能优化

- **字典缓存**：字典数据自动缓存到 Redis，默认保留7天
- **切面控制**：使用 `@I18nControl` 注解控制切面执行，避免不必要的处理
- **批量注册**：使用 `addI18nDictionaries` 批量注册字典，减少 Redis 操作

---

## ❓ 常见问题

### Q1: 如何切换语言？

**A:** 组件使用 Spring 的 `LocaleContextHolder` 获取当前区域。可以通过以下方式切换：

**方式一：通过请求头（推荐）**

```http
GET /api/users
X-RD-Request-Language: en-US
```

或者使用标准的 `Accept-Language` 头：

```http
GET /api/users
Accept-Language: en-US,en;q=0.9
```

**方式二：在代码中设置**

```java
import org.springframework.context.i18n.LocaleContextHolder;

// 设置当前线程的 Locale
LocaleContextHolder.setLocale(Locale.US);

// 获取国际化文本
String message = I18n.get("welcome.message");  // 使用 en-US

// 注意：在异步任务或线程池中，Locale 可能丢失，建议显式传递
```

**方式三：直接指定 Locale**

```java
// 不依赖请求上下文，直接指定 Locale
String message = I18n.get(Locale.US, "welcome.message");
```

**方式四：实现自定义 LocaleResolver**

```java
@Component
public class CustomLocaleResolver implements LocaleResolver {
    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        // 自定义解析逻辑
        return Locale.forLanguageTag("en-US");
    }
    
    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        // 设置区域
    }
}
```

**Locale 获取优先级**：

1. 请求头 `X_RD_REQUEST_LANGUAGE`（如果存在且有效）
2. 配置的默认 Locale（`platform.component.i18n.default-locale`，默认：`zh_CN`）
3. 请求的 Locale（`request.getLocale()`，来自 `Accept-Language` 头）

### Q2: 资源文件找不到怎么办？

**A:** 
- 检查文件路径是否正确（默认：`classpath:i18n/messages`）
- 检查文件命名是否正确（如：`messages_zh_CN.properties`）
- 检查配置中的 `path` 是否正确

### Q3: 字典国际化不生效？

**A:** 
- 确保已启用切面：`enable-aspect-i18n: true`
- 确保 Controller 上有 `@I18nControl` 注解
- 确保字段上有 `@I18nDict` 注解
- 确保字典已注册到 Redis

### Q4: 如何获取当前语言环境？

**A:** 

```java
import org.springframework.context.i18n.LocaleContextHolder;

Locale locale = LocaleContextHolder.getLocale();
// 注意：在非 Web 环境中可能返回 null，组件会自动回退到配置的 defaultLocale

if (locale != null) {
    String language = locale.getLanguage();  // zh, en, ja
    String country = locale.getCountry();    // CN, US, JP
} else {
    // 在非 Web 环境中，使用配置的默认 Locale
    locale = Locale.CHINA;  // 或从 I18nProperties 获取
}
```

**推荐方式**：直接使用 `I18n.get()` 方法，组件会自动处理 Locale 的获取和回退：

```java
// 组件内部会自动处理 Locale 获取和回退逻辑
String message = I18n.get("welcome.message");
// 1. 从 LocaleContextHolder 获取 Locale
// 2. 如果为 null，使用配置的 defaultLocale
// 3. 使用获取到的 Locale 查找资源文件
```

### Q5: 字典数据如何更新？

**A:** 

```java
// 更新单个字典
Map<String, String> localeMap = Map.of(
    "zh_CN", "新值",
    "en_US", "New Value"
);
I18nHandle.addI18nDictionary("dict.key", localeMap);

// 删除字典
I18nHandle.deleteI18nDictionary("dict.key");
```

### Q6: 如何禁用切面国际化？

**A:** 在配置中禁用：

```yaml
platform:
  component:
    i18n:
      enable-aspect-i18n: false
```

### Q7: 资源文件支持哪些格式？

**A:** 支持标准的 Java Properties 文件格式，使用 UTF-8 编码。

---

## 📝 总结

Richie I18n Component 提供了完整的国际化解决方案，支持资源文件国际化和字典国际化两种方式。通过合理使用切面和注解，可以自动处理国际化逻辑，提升开发效率。

**关键要点**：

1. **资源文件国际化**：适合静态文本，使用 `I18n.get()` 获取
2. **字典国际化**：适合动态数据，使用 `I18nHandle` 管理
3. **自动注入**：使用 `@I18nControl` 和 `@I18nDict` 实现自动注入
4. **性能优化**：字典数据缓存到 Redis，提升查询性能
5. **灵活配置**：支持自定义区域、资源文件路径等
6. **Locale 获取流程**：遵循明确的优先级规则（请求头 → 配置默认值 → 请求 Locale），确保在各种环境下都能正确获取语言环境

通过遵循这些最佳实践，可以构建多语言、高性能的国际化应用。

