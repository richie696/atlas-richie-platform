/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.storage.config;

import cn.richie696.component.storage.bean.FtpConfig;
import cn.richie696.component.storage.core.StorageEngine;
import cn.richie696.component.storage.core.impl.FtpStorageEngine;
import cn.richie696.component.storage.enums.StorageEngineEnum;
import cn.richie696.component.storage.pool.FtpClientPool;
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
