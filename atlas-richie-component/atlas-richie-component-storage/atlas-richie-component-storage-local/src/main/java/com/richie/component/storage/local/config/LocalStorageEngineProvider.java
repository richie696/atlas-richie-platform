package com.richie.component.storage.local.config;

import com.richie.component.storage.bean.LocalConfig;
import com.richie.component.storage.config.ConfigValidation;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.core.StorageEngine;
import com.richie.component.storage.core.StorageEngineProvider;
import com.richie.component.storage.local.core.impl.LocalStorageEngine;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.component.storage.local.repository.mapper.FileMetadataMapper;
import com.richie.context.common.api.SpringContextHolder;
import lombok.extern.slf4j.Slf4j;

/**
 * 本地存储引擎 Provider
 * <p>
 * 支持自动模式和手动模式。手动模式下从 Spring 上下文获取 FileMetadataMapper，
 * 并根据传入的 StorageProperties 创建引擎实例。
 *
 * @author richie696
 * @version 1.0
 * @since 2024-01-01
 */
@Slf4j
public class LocalStorageEngineProvider implements StorageEngineProvider {

    @Override
    public StorageEngineEnum supportedEngineType() {
        return StorageEngineEnum.LOCAL;
    }

    @Override
    public StorageEngine create(StorageProperties properties) {
        LocalConfig localConfig = properties.getLocal();
        LocalStorageEngine engine = new LocalStorageEngine(properties, localConfig);
        // 手动模式下从 Spring 上下文获取 Mapper（产品部署时一定存在）
        FileMetadataMapper mapper = SpringContextHolder.getBean(FileMetadataMapper.class);
        engine.setFileMetadataMapper(mapper);
        return engine;
    }

    @Override
    public void validate(StorageProperties properties) {
        LocalConfig c = properties.getLocal();
        ConfigValidation.requireNonNull(c, "本地存储配置 (local)");
        ConfigValidation.requireNonBlank(c.getPath(), "本地存储 path");
    }
}
