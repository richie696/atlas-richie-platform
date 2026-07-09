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
