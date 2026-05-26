package com.richie.component.storage.core.impl;

import com.richie.context.utils.data.JsonUtils;
import com.richie.component.storage.bean.DownloadContext;
import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.bean.DirectUploadPolicy;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.bean.image.ImageOptions;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.converter.StorageTypeConverter;
import com.richie.component.storage.core.StorageEngine;
import cn.hutool.core.lang.UUID;
import tools.jackson.core.type.TypeReference;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.CannedAccessControlList;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.StorageClass;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.qcloud.cos.transfer.Download;
import com.qcloud.cos.transfer.TransferManager;
import com.qcloud.cos.transfer.TransferManagerConfiguration;
import com.qcloud.cos.utils.UrlEncoderUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * 腾讯云COS对象存储引擎
 *
 * @author richie696
 * @version 1.0
 * @since 2024-01-05 17:41:53
 */
@Slf4j
@Service("objectStorageEngine")
@ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "tencent_cos")
public final class CosStorageEngine extends AbstractObjectStorageEngine<COSClient>
        implements StorageEngine {

    public CosStorageEngine(StorageProperties properties,
                            StorageTypeConverter converter) {
        super(properties, converter);
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull File file) {
        return putImage(key, file, null);
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull InputStream inputStream) {
        return putImage(key, inputStream, null);
    }

    @Override
    public UploadResponse putImage(@Nonnull String key, @Nonnull File file, ImageOptions options) {
        String realKey = getRealPath(key);
        return update(key, file, options, t -> new PutObjectRequest(getBucketName(), realKey, t));
    }

    @Override
    public UploadResponse putImage(@Nonnull String key, @Nonnull InputStream inputStream, ImageOptions options) {
        String realKey = getRealPath(key);
        return update(key, inputStream, options, _ -> {
            var objectMetadata = new ObjectMetadata();
            // 创建推送请求对象
            var putObjectRequest = new PutObjectRequest(
                    getBucketName(), realKey, inputStream, objectMetadata);
        /*
            设置存储类型（如有需要，不需要请忽略此行代码）, 默认是标准(Standard), 低频(standard_ia)
            更多存储类型请参见 https://cloud.tencent.com/document/product/436/33417
         */
            putObjectRequest.setStorageClass(StorageClass.fromValue(getStorageClass()));
            // 设置 ACL（访问控制列表）
            CannedAccessControlList acl = getAcl();
            if (acl != null) {
                try {
                    putObjectRequest.setCannedAcl(acl);
                } catch (Exception e) {
                    log.warn("设置 ACL 失败: {}, 将使用默认 ACL (PRIVATE). 错误: {}", acl, e.getMessage());
                }
            }
            return putObjectRequest;
        });
    }

    @Override
    public <T> DownloadResponse<T> getData(@Nonnull String key, @Nonnull TypeReference<T> typeReference) {
        key = getRealPath(key);
        var cosClient = getClient(COSClient.class);
        try (var cosObject = cosClient.getObject(new GetObjectRequest(getBucketName(), key));
             var is = cosObject.getObjectContent()) {
            if (is == null) {
                log.warn("cosClient.getObject null");
                DownloadResponse<T> error = new DownloadResponse<>();
                error.setSuccess(false)
                        .setErrorMessage("文件不存在")
                        .setKey(getResourceKey(key));
                return error;
            }
            T data = JsonUtils.getInstance().deserialize(is, typeReference);
            if (data == null) {
                log.warn("cosClient.getObject deserialize null");
                DownloadResponse<T> error = new DownloadResponse<>();
                error.setSuccess(false)
                        .setErrorMessage("文件解析错误")
                        .setKey(getResourceKey(key));
                return error;
            }
            DownloadResponse<T> result = new DownloadResponse<>();
            result.setSuccess(true)
                    .setVersionId(cosObject.getObjectMetadata().getVersionId())
                    .setRequestId(cosObject.getObjectMetadata().getRequestId())
                    .setBucketName(cosObject.getBucketName())
                    .setKey(getResourceKey(key));
            return result.setData(data);
        } catch (IOException e) {
            log.error("cosClient.getObject IOException", e);
            DownloadResponse<T> error = new DownloadResponse<>();
            error.setSuccess(false)
                    .setErrorMessage("文件读取错误")
                    .setKey(getResourceKey(key));
            return error;
        } finally {
            destroy(cosClient);
        }
    }

    @Override
    public DownloadResponse<byte[]> getObject(@Nonnull String key, @Nonnull File targetPath, boolean returnData) {
        key = getRealPath(key);
        if (!targetPath.canWrite()) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage("The directory does not have permission to write to files.")
                    .setBucketName(getBucketName())
                    .setRequestId(UUID.fastUUID().toString(true))
                    .setKey(key);
        }
        return download(returnData, key, targetPath, context -> {
            var transferManager = context.transferManager();
            return transferManager.download(context.request(), context.targetFile());
        });
    }

    @Override
    public DownloadResponse<byte[]> getResumableObject(@Nonnull String key, @Nonnull String targetPath, boolean returnData) {
        key = getRealPath(key);
        var targetFile = new File(targetPath);
        if (!targetFile.canWrite()) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage("The directory does not have permission to write to files.")
                    .setBucketName(getBucketName())
                    .setRequestId(UUID.fastUUID().toString(true))
                    .setKey(key);
        }
        return download(returnData, key, targetFile, context -> {
            var transferManager = context.transferManager();
            return transferManager.download(context.request(), context.targetFile(), true);
        });
    }

    @Override
    public boolean existsObject(@Nonnull String key) {
        key = getRealPath(key);
        COSClient cosClient = getClient(COSClient.class);
        try {
            return cosClient.doesObjectExist(getBucketName(), key);
        } finally {
            destroy(cosClient);
        }
    }

    @Override
    public DirectUploadPolicy issueDirectUploadPolicy(@Nonnull String key, int expireSeconds) {
        int safeExpire = Math.max(expireSeconds, 60);
        String realKey = getRealPath(key);
        COSClient cosClient = getClient(COSClient.class);
        try {
            Date expiration = Date.from(Instant.now().plusSeconds(safeExpire));
            URL url = cosClient.generatePresignedUrl(getBucketName(), realKey, expiration, HttpMethodName.PUT);
            return DirectUploadPolicy.builder()
                    .success(true)
                    .method("PUT")
                    .uploadUrl(url.toString())
                    .headers(Map.of())
                    .formFields(Map.of())
                    .bucketName(getBucketName())
                    .key(realKey)
                    .expireAt(OffsetDateTime.ofInstant(expiration.toInstant(), ZoneId.systemDefault()))
                    .fallback(false)
                    .build();
        } catch (Exception e) {
            log.warn("COS 预签名签发失败，降级兜底直传链接。key={}, error={}", realKey, e.getMessage());
            return buildFallbackDirectUploadPolicy(key, safeExpire);
        } finally {
            destroy(cosClient);
        }
    }

    private <T> UploadResponse update(String key, T t, ImageOptions options, Function<T, PutObjectRequest> function) {
        key = getRealPath(key);
        var transferManager = createTransferManager();
        var putObjectRequest = function.apply(t);
        if (putObjectRequest == null) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage("文件流错误，无法正确读取。")
                    .build();
        }
        handlerImageFile(putObjectRequest, options);
        // 设置 ACL（访问控制列表）
        CannedAccessControlList acl = getAcl();
        if (acl != null) {
            try {
                putObjectRequest.setCannedAcl(acl);
            } catch (Exception e) {
                log.warn("设置 ACL 失败: {}, 将使用默认 ACL (PRIVATE). 错误: {}", acl, e.getMessage());
            }
        }
        var upload = transferManager.upload(putObjectRequest);
        try {
            var result = upload.waitForUploadResult();
            return UploadResponse.builder()
                    .success(true)
                    .requestId(result.getRequestId())
                    .key(getResourceKey(key))
                    .uploadTime(OffsetDateTime.now())
                    .hashValue(result.getCrc64Ecma())
                    .bucketName(getBucketName())
                    .versionId(result.getVersionId())
                    .url("https://" + getBucketName() + "." + objectConfig().getEndpoint() + "/" + key)
                    .build();
        } catch (InterruptedException e) {
            log.error("[Storage] COS upload interrupted exception: {}", e.getMessage());
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        } finally {
            transferManager.shutdownNow(true);
        }
    }

    private void handlerImageFile(PutObjectRequest putObjectRequest, ImageOptions options) {
        if (options == null) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        if (options.isExtremeCompression()) {
            builder.append("/imageSlim");
        } else {
            builder.append("imageMogr2/strip/ignore-error/1");
            Optional.ofNullable(options.getScale()).ifPresent(scale -> builder.append("/thumbnail/!").append(scale).append("p"));
            Optional.ofNullable(options.getCrop()).ifPresent(crop -> builder.append("/iradius/").append(crop));
            Optional.ofNullable(options.getRotate()).ifPresent(rotate -> builder.append("/rotate/").append(rotate));
            Optional.ofNullable(options.getQuality()).ifPresent(quality -> builder.append("/quality/").append(quality).append("!/minsize/1"));
        }
        Optional.ofNullable(options.getFormat()).ifPresent(format -> builder.append("/format/").append(format.getFormat()));
        // 构建规则
        var pictureRule = new PicOperations.Rule();
        pictureRule.setBucket(getBucketName());
        pictureRule.setFileId(UrlEncoderUtils.encode("/" + putObjectRequest.getKey()));
        pictureRule.setRule(builder.toString());
        // 创建操作对象
        var picOperations = new PicOperations();
        picOperations.setRules(List.of(pictureRule));
        var headerString = JsonUtils.getInstance().serialize(picOperations);
        Objects.requireNonNull(headerString, "图片处理参数序列化失败。");
        putObjectRequest.putCustomRequestHeader("Pic-Operations", headerString);
    }

    private <T> DownloadResponse<T> download(boolean returnData, String key, File targetFilePath, Function<DownloadContext, Download> function) {
        var transferManager = createTransferManager();
        var getObjectRequest = new GetObjectRequest(getBucketName(), key);
        var download = function.apply(
                new DownloadContext(transferManager, getObjectRequest, targetFilePath)
        );
        try (var fis = new FileInputStream(targetFilePath)) {
            download.waitForCompletion();
            DownloadResponse<T> result = new DownloadResponse<T>()
                    .setSuccess(true)
                    .setKey(download.getKey())
                    .setBucketName(getBucketName())
                    .setRequestId(download.getObjectMetadata().getRequestId())
                    .setVersionId(download.getObjectMetadata().getVersionId());
            if (returnData) {
                result.setData((T) fis.readAllBytes());
            }
            return result;
        } catch (InterruptedException e) {
            log.error("[Storage] COS download interrupted exception: {}", e.getMessage());
            return new DownloadResponse<T>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            transferManager.shutdownNow(true);
        }
    }

    private TransferManager createTransferManager() {
        // 自定义线程池大小，建议在客户端与 COS 网络充足（例如使用腾讯云的 CVM，同地域上传 COS）的情况下，设置成16或32即可，可较充分的利用网络资源
        // 对于使用公网传输且网络带宽质量不高的情况，建议减小该值，避免因网速过慢，造成请求超时。
        var threadPool = Executors.newFixedThreadPool(32);

        var cosClient = getClient(COSClient.class);
        // 传入一个 threadpool, 若不传入线程池，默认 TransferManager 中会生成一个单线程的线程池。
        var transferManager = new TransferManager(cosClient, threadPool);

        // 设置高级接口的配置项
        // 分块上传阈值和分块大小分别为 5MB 和 1MB
        var transferManagerConfiguration = new TransferManagerConfiguration();
        transferManagerConfiguration.setMultipartUploadThreshold(5 * 1024 * 1024);
        transferManagerConfiguration.setMinimumUploadPartSize(1024 * 1024);
        transferManager.setConfiguration(transferManagerConfiguration);
        return transferManager;
    }

    @Override
    void destroy(@Nonnull COSClient cosClient) {
        cosClient.shutdown();
    }
}
