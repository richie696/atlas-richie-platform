package com.richie.component.storage.config;

import com.richie.component.storage.bean.Smb3Config;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.core.StorageEngine;
import com.richie.component.storage.core.StorageEngineProvider;
import com.richie.component.storage.core.impl.SmbStorageEngine;
import com.richie.component.storage.enums.StorageEngineEnum;
import lombok.extern.slf4j.Slf4j;
import org.codelibs.jcifs.smb.CIFSContext;
import org.codelibs.jcifs.smb.CIFSException;
import org.codelibs.jcifs.smb.config.PropertyConfiguration;
import org.codelibs.jcifs.smb.context.BaseContext;
import org.codelibs.jcifs.smb.impl.NtlmPasswordAuthenticator;

import java.util.Properties;

@Slf4j
public class SmbStorageEngineProvider implements StorageEngineProvider {

    @Override
    public StorageEngineEnum supportedEngineType() {
        return StorageEngineEnum.SMB;
    }

    @Override
    public StorageEngine create(StorageProperties properties) {
        Smb3Config smb3 = properties.getSmb3();
        try {
            Properties ps = new Properties();
            ps.setProperty("jcifs.smb.client.dfs.disabled", Boolean.toString(smb3.isDfs()));
            ps.setProperty("jcifs.smb.client.dfs.ttl", smb3.getDfsTtl().toString());
            ps.setProperty("jcifs.smb.client.dfs.strictView", Boolean.toString(smb3.isStrictView()));
            ps.setProperty("jcifs.smb.client.dfs.convertToFQDN", Boolean.toString(smb3.isConvertToFQDN()));
            BaseContext baseContext = new BaseContext(new PropertyConfiguration(ps));
            NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator("WORKGROUP", smb3.getUsername(), smb3.getPassword());
            CIFSContext context = baseContext.withCredentials(auth);
            return new SmbStorageEngine(properties, context);
        } catch (CIFSException e) {
            throw new IllegalStateException("SMB CIFS 上下文创建失败", e);
        }
    }

    @Override
    public void destroy(StorageEngine engine) {
        log.info("SMB 引擎已销毁");
    }

    @Override
    public void validate(StorageProperties properties) {
        Smb3Config c = properties.getSmb3();
        ConfigValidation.requireNonNull(c, "SMB 配置");
        ConfigValidation.requireNonBlank(c.getUsername(), "SMB username");
        ConfigValidation.requireNonBlank(c.getPassword(), "SMB password");
        ConfigValidation.requireNonBlank(c.getDomain(), "SMB domain");
    }
}
