package com.richie.component.storage.core.impl;

import com.richie.context.utils.data.JsonUtils;
import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.bean.DirectUploadPolicy;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.bean.image.ImageOptions;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.converter.StorageTypeConverter;
import com.richie.component.storage.core.StorageEngine;
import java.util.UUID;
import tools.jackson.core.type.TypeReference;
import com.volcengine.tos.TOSV2;
import com.volcengine.tos.TosClientException;
import com.volcengine.tos.TosServerException;
import com.volcengine.tos.comm.common.ACLType;
import com.volcengine.tos.comm.common.StorageClassType;
import com.volcengine.tos.model.object.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.Map;


/**
 * 火山引擎TOS对象存储引擎
 *
 * @author richie696
 * @version 1.0
 * @since 2024-01-05 17:43:05
 */
@Slf4j
@Service("objectStorageEngine")
@ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "volcengine_tos")
public final class TosStorageEngine extends AbstractObjectStorageEngine<TOSV2> implements StorageEngine {

    public TosStorageEngine(StorageProperties properties,
                            StorageTypeConverter converter) {
        super(properties, converter);
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull File file) {
        key = getRealPath(key);
        var client = getClient(TOSV2.class);
        try (var inputStream = new ByteArrayInputStream(Files.readAllBytes(file.toPath()))) {
            return getUploadResponse(key, inputStream, client);
        } catch (TosClientException oe) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(oe.getMessage())
                    .bucketName(getBucketName())
                    .key(key)
                    .build();
        } catch (TosServerException ce) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(ce.getMessage())
                    .requestId(ce.getRequestID())
                    .bucketName(getBucketName())
                    .key(key)
                    .build();
        } catch (IOException e) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage("File read failed, key: " + key)
                    .bucketName(getBucketName())
                    .key(key)
                    .build();
        } finally {
            destroy(client);
        }
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull InputStream inputStream) {
        key = getRealPath(key);
        var client = getClient(TOSV2.class);
        try {
            return getUploadResponse(key, inputStream, client);
        } catch (TosClientException oe) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(oe.getMessage())
                    .bucketName(getBucketName())
                    .key(key)
                    .build();
        } catch (TosServerException ce) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(ce.getMessage())
                    .bucketName(getBucketName())
                    .key(key)
                    .requestId(ce.getRequestID())
                    .build();
        } finally {
            destroy(client);
        }
    }

    @Override
    public UploadResponse putImage(@Nonnull String key, @Nonnull File file, ImageOptions options) {
        return putObject(key, file);
    }

    @Override
    public UploadResponse putImage(@Nonnull String key, @Nonnull InputStream inputStream, ImageOptions options) {
        return putObject(key, inputStream);
    }

    @Override
    public <T> DownloadResponse<T> getData(@Nonnull String key, @Nonnull TypeReference<T> typeReference) {
        key = getRealPath(key);
        var client = getClient(TOSV2.class);
        var input = new GetObjectV2Input().setBucket(getBucketName()).setKey(key);
        var builder = new StringBuilder();
        try (var output = client.getObject(input);
             var reader = new BufferedReader(new InputStreamReader(output.getContent()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return new DownloadResponse<T>()
                    .setSuccess(true)
                    .setErrorMessage(null)
                    .setBucketName(getBucketName())
                    .setRequestId(output.getRequestInfo().getRequestId())
                    .setKey(key)
                    .setData(JsonUtils.getInstance().deserialize(builder.toString(), typeReference));
        } catch (IOException e) {
            return new DownloadResponse<T>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setBucketName(getBucketName())
                    .setKey(key);
        } catch (TosClientException e) {
            // 操作失败，捕获客户端异常，一般情况是请求参数错误，此时请求并未发送
            return new DownloadResponse<T>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setBucketName(getBucketName())
                    .setKey(key);
        } catch (TosServerException e) {
            // 操作失败，捕获服务端异常，可以获取到从服务端返回的详细错误信息
            return new DownloadResponse<T>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage() + "(" + e.getCode() + ")")
                    .setBucketName(getBucketName())
                    .setKey(key)
                    .setRequestId(e.getRequestID());
        } catch (Throwable t) {
            // 作为兜底捕获其他异常，一般不会执行到这里
            return new DownloadResponse<T>()
                    .setSuccess(false)
                    .setErrorMessage(t.getMessage())
                    .setBucketName(getBucketName())
                    .setKey(key);
        } finally {
            destroy(client);
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
                    .setRequestId(UUID.randomUUID().toString().replace("-", ""))
                    .setKey(key);
        }
        var client = getClient(TOSV2.class);
        var input = new GetObjectV2Input().setBucket(getBucketName()).setKey(key);
        try (var output = client.getObject(input);
             var inputStream = new BufferedInputStream(output.getContent());
             var outputStream = new BufferedOutputStream(new FileOutputStream(targetPath))) {
            var buffer = new byte[1024 * 1024];
            int bytesRead;
            // 循环获取输入流，并写入文件
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            if (returnData) {
                try (var byteArrayOutputStream = new ByteArrayOutputStream()) {
                    var data = new byte[1024];
                    int length;
                    while ((length = inputStream.read(data)) != -1) {
                        byteArrayOutputStream.write(data, 0, length);
                    }
                    return new DownloadResponse<byte[]>()
                            .setSuccess(true)
                            .setErrorMessage(null)
                            .setBucketName(getBucketName())
                            .setRequestId(output.getRequestInfo().getRequestId())
                            .setVersionId(output.getVersionID())
                            .setKey(key)
                            .setData(byteArrayOutputStream.toByteArray());
                }
            }
            return new DownloadResponse<byte[]>()
                    .setSuccess(true)
                    .setBucketName(getBucketName())
                    .setRequestId(output.getRequestInfo().getRequestId())
                    .setKey(key)
                    .setVersionId(output.getVersionID())
                    .setContentType(output.getContentType())
                    .setContentMD5(output.getContentMD5())
                    .setContentEncoding(output.getContentEncoding());
        } catch (IOException e) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setBucketName(getBucketName())
                    .setKey(key);
        } catch (TosClientException e) {
            // 操作失败，捕获客户端异常，一般情况是请求参数错误，此时请求并未发送
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setBucketName(getBucketName())
                    .setKey(key);
        } catch (TosServerException e) {
            // 操作失败，捕获服务端异常，可以获取到从服务端返回的详细错误信息
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage() + "(" + e.getCode() + ")")
                    .setBucketName(getBucketName())
                    .setKey(key)
                    .setRequestId(e.getRequestID());
        } catch (Throwable t) {
            // 作为兜底捕获其他异常，一般不会执行到这里
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage(t.getMessage())
                    .setBucketName(getBucketName())
                    .setKey(key);
        } finally {
            destroy(client);
        }
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
                    .setRequestId(UUID.randomUUID().toString().replace("-", ""))
                    .setKey(key);
        }
        var client = getClient(TOSV2.class);
        var input = new DownloadFileInput()
                .setBucket(getBucketName())     // 桶
                .setKey(key)        // 文件路径
                .setFilePath(targetPath)        // 本地文件路径
                .setEnableCheckpoint(true)  // 开启断点续传
                .setPartSize(10 * 1024 * 1024)      // 文件分块大小
                .setTaskNum(10);            // 并行下载线程数
        try {
            var output = client.downloadFile(input);
            if (returnData) {
                // 通过 nio 读取 targetFile 文件内容并转换为字节流
                return new DownloadResponse<byte[]>()
                        .setSuccess(true)
                        .setErrorMessage(null)
                        .setBucketName(getBucketName())
                        .setRequestId(output.getRequestInfo().getRequestId())
                        .setVersionId(output.getVersionID())
                        .setKey(key)
                        .setData(Files.readAllBytes(targetFile.toPath()));
            }
            return new DownloadResponse<byte[]>()
                    .setSuccess(true)
                    .setBucketName(getBucketName())
                    .setRequestId(output.getRequestInfo().getRequestId())
                    .setKey(key)
                    .setVersionId(output.getVersionID())
                    .setContentType(output.getContentType())
                    .setContentMD5(output.getContentMD5())
                    .setContentEncoding(output.getContentEncoding());
        } catch (TosClientException e) {
            // 操作失败，捕获客户端异常，一般情况是请求参数错误，此时请求并未发送
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setBucketName(getBucketName())
                    .setKey(key);
        } catch (TosServerException e) {
            // 操作失败，捕获服务端异常，可以获取到从服务端返回的详细错误信息
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage() + "(" + e.getCode() + ")")
                    .setBucketName(getBucketName())
                    .setKey(key)
                    .setRequestId(e.getRequestID());
        } catch (Throwable t) {
            // 作为兜底捕获其他异常，一般不会执行到这里
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage(t.getMessage())
                    .setBucketName(getBucketName())
                    .setKey(key);
        } finally {
            destroy(client);
        }
    }

    @Override
    public boolean existsObject(@Nonnull String key) {
        key = getRealPath(key);
        var client = getClient(TOSV2.class);
        try {
            client.headObject(HeadObjectV2Input.builder().bucket(getBucketName()).key(key).build());
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            destroy(client);
        }
    }

    @Override
    public DirectUploadPolicy issueDirectUploadPolicy(@Nonnull String key, int expireSeconds) {
        int safeExpire = Math.max(expireSeconds, 60);
        String realKey = getRealPath(key);
        var client = getClient(TOSV2.class);
        try {
            String signedUrl = invokeTosPresign(client, getBucketName(), realKey, safeExpire);
            return DirectUploadPolicy.builder()
                    .success(true)
                    .method("PUT")
                    .uploadUrl(signedUrl)
                    .headers(Map.of())
                    .formFields(Map.of())
                    .bucketName(getBucketName())
                    .key(realKey)
                    .expireAt(OffsetDateTime.now().plusSeconds(safeExpire))
                    .fallback(false)
                    .build();
        } catch (Exception e) {
            log.warn("TOS 预签名签发失败，降级兜底直传链接。key={}, error={}", realKey, e.getMessage());
            return buildFallbackDirectUploadPolicy(key, safeExpire);
        } finally {
            destroy(client);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String invokeTosPresign(TOSV2 client, String bucket, String key, int expireSeconds) throws Exception {
        // 优先尝试常见签名方法：preSignedURL(PreSignedURLInput)
        for (var method : client.getClass().getMethods()) {
            String methodName = method.getName();
            if (!("preSignedURL".equals(methodName) || "preSignedUrl".equals(methodName))) {
                continue;
            }
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length != 1) {
                continue;
            }
            Object input = buildTosPreSignedInput(paramTypes[0], bucket, key, expireSeconds);
            Object output = method.invoke(client, input);
            return String.valueOf(output);
        }
        // 次选：generatePresignedUrl(bucket,key,expire,methodEnum)
        for (var method : client.getClass().getMethods()) {
            if (!"generatePresignedUrl".equals(method.getName())) {
                continue;
            }
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length == 4
                    && paramTypes[0] == String.class
                    && paramTypes[1] == String.class
                    && (paramTypes[2] == int.class || paramTypes[2] == Integer.class)
                    && paramTypes[3].isEnum()) {
                Enum put = Enum.valueOf((Class<? extends Enum>) paramTypes[3], "PUT");
                Object url = method.invoke(client, bucket, key, expireSeconds, put);
                return String.valueOf(url);
            }
        }
        throw new NoSuchMethodException("TOS presign method not found");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object buildTosPreSignedInput(Class<?> inputClass, String bucket, String key, int expireSeconds) throws Exception {
        // builder 风格
        try {
            Object builder = inputClass.getMethod("builder").invoke(null);
            invokeIfPresent(builder, "bucket", String.class, bucket);
            invokeIfPresent(builder, "setBucket", String.class, bucket);
            invokeIfPresent(builder, "key", String.class, key);
            invokeIfPresent(builder, "setKey", String.class, key);
            invokeIfPresent(builder, "expires", int.class, expireSeconds);
            invokeIfPresent(builder, "setExpires", int.class, expireSeconds);
            Class<? extends Enum> httpMethodClass =
                    (Class<? extends Enum>) Class.forName("com.volcengine.tos.comm.HttpMethod");
            Enum put = Enum.valueOf(httpMethodClass, "PUT");
            invokeIfPresent(builder, "httpMethod", httpMethodClass, put);
            invokeIfPresent(builder, "setHttpMethod", httpMethodClass, put);
            return builder.getClass().getMethod("build").invoke(builder);
        } catch (NoSuchMethodException ignore) {
            Object input = inputClass.getConstructor().newInstance();
            invokeIfPresent(input, "setBucket", String.class, bucket);
            invokeIfPresent(input, "setKey", String.class, key);
            invokeIfPresent(input, "setExpires", int.class, expireSeconds);
            try {
                Class<? extends Enum> httpMethodClass =
                        (Class<? extends Enum>) Class.forName("com.volcengine.tos.comm.HttpMethod");
                Enum put = Enum.valueOf(httpMethodClass, "PUT");
                invokeIfPresent(input, "setHttpMethod", httpMethodClass, put);
            } catch (ClassNotFoundException ignored) {
            }
            return input;
        }
    }

    private void invokeIfPresent(Object target, String methodName, Class<?> argType, Object arg) {
        try {
            target.getClass().getMethod(methodName, argType).invoke(target, arg);
        } catch (Exception ignore) {
        }
    }

    private UploadResponse getUploadResponse(String key, @Nonnull InputStream inputStream, TOSV2 client) {
        // 设置请求参数和请求头
        ObjectMetaRequestOptions options = new ObjectMetaRequestOptions();
        var storageClass = getStorageClass();
        options.setStorageClass(StorageClassType.valueOf(storageClass));
        var putObjectInput = new PutObjectInput()
                .setOptions(options)
                .setBucket(getBucketName())
                .setKey(key)
                .setContent(inputStream);
        // 执行上传操作
        var output = client.putObject(putObjectInput);

        // 设置 ACL（访问控制列表）- 上传后单独设置 ACL
        ACLType acl = getAcl();
        if (acl != null) {
            try {
                var putObjectAclInput = new PutObjectACLInput()
                        .setBucket(getBucketName())
                        .setKey(key)
                        .setAcl(acl);
                client.putObjectAcl(putObjectAclInput);
            } catch (Exception e) {
                log.warn("设置 ACL 失败: {}, 将使用默认 ACL (PRIVATE). 错误: {}", acl, e.getMessage());
            }
        }
        return UploadResponse.builder()
                .success(true)
                .bucketName(getBucketName())
                .versionId(output.getVersionID())
                .requestId(output.getRequestInfo().getRequestId())
                .hashValue(output.getEtag())
                .uploadTime(OffsetDateTime.now())
                .url("https://" + getBucketName() + "." + objectConfig().getEndpoint() + "/" + key)
                .build();
    }

    @Override
    void destroy(@Nonnull TOSV2 client) {
        super.destroy(client);
    }
}
