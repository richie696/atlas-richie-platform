package com.richie.component.storage.core.impl;

import com.richie.context.utils.data.JsonUtils;
import com.richie.context.utils.security.HashUtils;
import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.bean.image.ImageOptions;
import com.richie.component.storage.config.StorageProperties;
import java.util.UUID;
import tools.jackson.core.type.TypeReference;
import jcifs.CIFSContext;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service("smbStorageEngine")
@ConditionalOnBean(CIFSContext.class)
@RequiredArgsConstructor
public final class SmbStorageEngine extends AbstractDestroyEngine<SmbFile> {

    private final StorageProperties properties;
    private final CIFSContext cifsContext;

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Map<?, ?> collection) {
        key = getRealPath(key);
        var serialize = JsonUtils.getInstance().serialize(collection, true);
        Objects.requireNonNull(serialize, "The serialized string cannot be null.");
        var hashValue = HashUtils.sha256(serialize);
        var inputStream = IOUtils.toInputStream(serialize, StandardCharsets.UTF_8);
        return getUploadResponse(key, hashValue, inputStream);
    }

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Collection<?> collection) {
        key = getRealPath(key);
        var serialize = JsonUtils.getInstance().serialize(collection, true);
        Objects.requireNonNull(serialize, "The serialized string cannot be null.");
        var hashValue = HashUtils.sha256(serialize);
        var inputStream = IOUtils.toInputStream(serialize, StandardCharsets.UTF_8);
        return getUploadResponse(key, hashValue, inputStream);
    }

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Object object) {
        key = getRealPath(key);
        var serialize = JsonUtils.getInstance().serialize(object, true);
        Objects.requireNonNull(serialize, "The serialized string cannot be null.");
        var hashValue = HashUtils.sha256(serialize);
        var inputStream = IOUtils.toInputStream(serialize, StandardCharsets.UTF_8);
        return getUploadResponse(key, hashValue, inputStream);
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull File file) {
        key = getRealPath(key);
        try (var smbFile = new SmbFile(getKeyPath(key), cifsContext);
             var smbFileOutputStream = new SmbFileOutputStream(smbFile);
             var inputStream = new FileInputStream(file)) {
            var buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                smbFileOutputStream.write(buffer, 0, length);
            }
            return UploadResponse.builder()
                    .success(true)
                    .key(key)
                    .uploadTime(OffsetDateTime.now())
                    .build();
        } catch (Exception e) {
            return UploadResponse.builder()
                    .success(false)
                    .key(key)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull InputStream inputStream) {
        key = getRealPath(key);
        try (var smbFile = new SmbFile(getKeyPath(key), cifsContext);
             var smbFileOutputStream = new SmbFileOutputStream(smbFile)) {
            var buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                smbFileOutputStream.write(buffer, 0, length);
            }
            return UploadResponse.builder()
                    .success(true)
                    .key(key)
                    .uploadTime(OffsetDateTime.now())
                    .build();
        } catch (Exception e) {
            return UploadResponse.builder()
                    .success(false)
                    .key(key)
                    .errorMessage(e.getMessage())
                    .build();
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
        var builder = new StringBuilder();
        try (var smbFile = new SmbFile(getKeyPath(key), cifsContext);
             var in = new SmbFileInputStream(smbFile);
             var reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return new DownloadResponse<T>()
                    .setSuccess(true)
                    .setKey(key)
                    .setVersionId(UUID.randomUUID().toString().replace("-", ""))
                    .setContentMD5("")
                    .setContentType("application/json")
                    .setContentEncoding(StandardCharsets.UTF_8.name())
                    .setData(JsonUtils.getInstance().deserialize(builder.toString(), typeReference));
        } catch (Exception e) {
            return new DownloadResponse<T>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setRequestId(UUID.randomUUID().toString().replace("-", ""))
                    .setKey(key);
        }
    }

    @Override
    public DownloadResponse<byte[]> getObject(@Nonnull String key, @Nonnull File targetPath, boolean returnData) {
        key = getRealPath(key);
        if (!targetPath.canWrite()) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage("The directory does not have permission to write to files.")
                    .setRequestId(UUID.randomUUID().toString().replace("-", ""))
                    .setKey(key);
        }
        try (var smbFile = new SmbFile(getKeyPath(key), cifsContext);
             var in = new SmbFileInputStream(smbFile);
             var out = new FileOutputStream(targetPath)) {
            var buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) != -1) {
                out.write(buffer, 0, length);
            }
            byte[] data = null;
            if (returnData) {
                data = Files.readAllBytes(targetPath.toPath());
            }
            return new DownloadResponse<byte[]>()
                    .setSuccess(true)
                    .setKey(key)
                    .setVersionId(UUID.randomUUID().toString().replace("-", ""))
                    .setContentMD5("")
                    .setContentType("application/json")
                    .setContentEncoding(StandardCharsets.UTF_8.name())
                    .setData(data);
        } catch (Exception e) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setRequestId(UUID.randomUUID().toString().replace("-", ""))
                    .setKey(key);
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
                    .setRequestId(UUID.randomUUID().toString().replace("-", ""))
                    .setKey(key);
        }
        return getObject(key, targetFile, returnData);
    }

    @Override
    public boolean existsObject(@Nonnull String key) {
        key = getRealPath(key);
        try (var smbFile = new SmbFile(getKeyPath(key), cifsContext)) {
            return smbFile.exists();
        } catch (Exception e) {
            return false;
        }
    }

    private URL getKeyPath(String key) throws MalformedURLException {
        return URI.create(getRealPath(key)).toURL();
    }

    private String getRealPath(String key) {
        return properties.getSmb3().getBasePath() + File.separator + key;
    }

    private UploadResponse getUploadResponse(String key, String hashValue, InputStream inputStream) {
        key = getRealPath(key);
        try (var smbFile = new SmbFile(getKeyPath(key), cifsContext);
             var smbFileOutputStream = new SmbFileOutputStream(smbFile)) {
            var buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                smbFileOutputStream.write(buffer, 0, length);
            }
            return UploadResponse.builder()
                    .success(true)
                    .key(key)
                    .hashValue(hashValue)
                    .build();
        } catch (Exception e) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

}
