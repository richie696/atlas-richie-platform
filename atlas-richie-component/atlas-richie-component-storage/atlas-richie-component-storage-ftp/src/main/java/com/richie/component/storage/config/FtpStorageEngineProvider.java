package com.richie.component.storage.config;

import com.richie.component.storage.bean.FtpConfig;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.core.StorageEngine;
import com.richie.component.storage.core.StorageEngineProvider;
import com.richie.component.storage.core.impl.FtpStorageEngine;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.component.storage.pool.FtpClientPool;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FtpStorageEngineProvider implements StorageEngineProvider {

    @Override
    public StorageEngineEnum supportedEngineType() {
        return StorageEngineEnum.FTP;
    }

    @Override
    public StorageEngine create(StorageProperties properties) {
        FtpConfig ftpConfig = properties.getFtp();
        FtpClientPool pool = new FtpClientPool(ftpConfig);
        return new FtpStorageEngine(properties, pool);
    }

    @Override
    public void destroy(StorageEngine engine) {
        if (engine instanceof FtpStorageEngine ftpEngine) {
            log.info("FTP 引擎已销毁");
        }
    }

    @Override
    public void validate(StorageProperties properties) {
        FtpConfig c = properties.getFtp();
        ConfigValidation.requireNonNull(c, "FTP 配置");
        ConfigValidation.requireNonBlank(c.getHost(), "FTP host");
    }
}
