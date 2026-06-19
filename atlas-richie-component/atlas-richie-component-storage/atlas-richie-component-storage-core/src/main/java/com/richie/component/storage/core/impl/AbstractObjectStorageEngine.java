package com.richie.component.storage.core.impl;

import com.richie.component.storage.bean.DirectDownloadPolicy;
import com.richie.component.storage.bean.DirectUploadPolicy;
import com.richie.context.utils.data.JsonUtils;
import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.converter.AclTypeConverter;
import com.richie.component.storage.converter.StorageTypeConverter;
import com.richie.component.storage.util.ObjectStorageKeys;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.Nonnull;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * 对象存储引擎抽象基类，提供配置读取、路径与 ACL 转换及 putData 等通用实现。
 *
 * @param <T> 底层客户端类型
 * @author richie696
 * @since 2023-09-05
 */
@RequiredArgsConstructor
public abstract class AbstractObjectStorageEngine<T> extends AbstractDestroyEngine<T> {

    /** 存储组件统一配置 */
    private final StorageProperties properties;

    /** 存储类型与引擎类型的转换器 */
    private final StorageTypeConverter converter;

    /**
     * ACL 类型转换器（可选，某些存储引擎可能不支持 ACL）
     */
    @Autowired(required = false)
    private AclTypeConverter<?> aclTypeConverter;

    /**
     * 获取 KEY 真实地址
     *
     * @param key 存储KEY
     * @return 返回真实KEY
     */
    protected String getRealPath(String key) {
        return ObjectStorageKeys.realPath(objectConfig().getBasePath(), key);
    }

    /**
     * 获取存储类型对应的引擎存储类名（如 STANDARD、IA 等）。
     *
     * @return 存储类字符串，未配置时为空串
     */
    protected String getStorageClass() {
        return Objects.requireNonNullElse(
                converter.convertToEngineType(properties.getObject().getStorageType()),
                ""
        );
    }

    /**
     * 获取 ACL（访问控制列表）配置
     * <p>
     * 如果配置了 ACL 且存在对应的转换器，则转换为引擎特定的 ACL 对象
     *
     * @param <T> ACL 对象类型
     * @return ACL 配置对象，如果未配置或转换器不存在则返回 null
     */
    @SuppressWarnings("unchecked")
    protected <T> T getAcl() {
        var aclType = objectConfig().getAcl();
        if (aclType == null || aclTypeConverter == null) {
            return null;
        }
        try {
            return (T) aclTypeConverter.convertToEngineAcl(aclType);
        } catch (Exception e) {
            // 如果转换失败，返回 null（使用默认 ACL）
            return null;
        }
    }

    /**
     * 获取对象存储配置
     *
     * @return 返回对象存储配置
     */
    public ObjectConfig objectConfig() {
        return properties.getObject();
    }

    /**
     * 获取桶名称的方法
     *
     * @return 返回桶名称
     */
    public String getBucketName() {
        return objectConfig().getBucketName();
    }

    /**
     * 获取目标资源键的方法
     *
     * @param key 资源键
     * @return 返回目标资源键
     */
    public String getResourceKey(String key) {
        return String.format("%s/%s", objectConfig().getEndpoint(), key);
    }

    protected String buildPublicObjectUrl(String realKey) {
        String endpoint = objectConfig().getEndpoint();
        if (StringUtils.isBlank(endpoint)) {
            return realKey;
        }
        String normalizedEndpoint = endpoint.startsWith("http://") || endpoint.startsWith("https://")
                ? endpoint
                : "https://" + endpoint;
        String stripped = normalizedEndpoint.replaceAll("/+$", "");
        if (stripped.contains(getBucketName() + ".")) {
            return stripped + "/" + realKey;
        }
        return "https://" + getBucketName() + "." + endpoint.replaceAll("^https?://", "").replaceAll("/+$", "") + "/" + realKey;
    }

    protected DirectUploadPolicy buildFallbackDirectUploadPolicy(String key, int expireSeconds) {
        int safeExpire = Math.max(expireSeconds, 60);
        String realKey = getRealPath(key);
        return DirectUploadPolicy.builder()
                .success(true)
                .errorMessage("当前引擎暂未接入官方预签名，返回可用兜底直传链接。")
                .method("PUT")
                .uploadUrl(buildPublicObjectUrl(realKey))
                .headers(Map.of())
                .formFields(Map.of())
                .bucketName(getBucketName())
                .key(realKey)
                .expireAt(OffsetDateTime.now().plusSeconds(safeExpire))
                .fallback(true)
                .build();
    }

    @Override
    public DirectUploadPolicy issueDirectUploadPolicy(String key, int expireSeconds) {
        return buildFallbackDirectUploadPolicy(key, expireSeconds);
    }

    /**
     * 构建兜底直读策略（当引擎未接入官方预签名时使用公开 URL 作为降级方案）。
     *
     * @param key           对象键
     * @param expireSeconds 有效期（秒）
     * @return 兜底直读策略
     */
    protected DirectDownloadPolicy buildFallbackDirectDownloadPolicy(String key, int expireSeconds) {
        int safeExpire = Math.max(expireSeconds, 60);
        String realKey = getRealPath(key);
        return DirectDownloadPolicy.builder()
                .success(true)
                .errorMessage("当前引擎暂未接入官方预签名，返回可用兜底直读链接。")
                .downloadUrl(buildPublicObjectUrl(realKey))
                .bucketName(getBucketName())
                .key(realKey)
                .expireAt(OffsetDateTime.now().plusSeconds(safeExpire))
                .fallback(true)
                .build();
    }

    @Override
    public DirectDownloadPolicy issueDirectDownloadPolicy(String key, int expireSeconds) {
        return buildFallbackDirectDownloadPolicy(key, expireSeconds);
    }

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Map<?, ?> collection) {
        var bytes = JsonUtils.getInstance().serializeBytes(collection);
        if (Objects.isNull(bytes)) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage("Data serialization error and cannot be read correctly.")
                    .build();
        }
        return putObject(key, new ByteArrayInputStream(bytes));
    }

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Collection<?> collection) {
        var bytes = JsonUtils.getInstance().serializeBytes(collection);
        if (Objects.isNull(bytes)) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage("Data serialization error and cannot be read correctly.")
                    .build();
        }
        return putObject(key, new ByteArrayInputStream(bytes));
    }

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Object object) {
        var bytes = JsonUtils.getInstance().serializeBytes(object);
        if (Objects.isNull(bytes)) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage("Data serialization error and cannot be read correctly.")
                    .build();
        }
        return putObject(key, new ByteArrayInputStream(bytes));
    }

}
