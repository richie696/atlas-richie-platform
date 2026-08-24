/* Copyright (c) 2026 Richie (https://www.github.com/richie696) */
package cn.richie696.component.secret.provider.aliyun;

import com.aliyun.kms20160120.models.DecryptRequest;
import com.aliyun.kms20160120.models.DecryptResponse;
import com.aliyun.kms20160120.models.EncryptRequest;
import com.aliyun.kms20160120.models.EncryptResponse;
import com.aliyun.kms20160120.models.GetSecretValueRequest;
import com.aliyun.kms20160120.models.GetSecretValueResponse;

interface AliyunKmsGateway extends AutoCloseable {
    GetSecretValueResponse getSecretValue(GetSecretValueRequest request) throws Exception;
    EncryptResponse encrypt(EncryptRequest request) throws Exception;
    DecryptResponse decrypt(DecryptRequest request) throws Exception;
    @Override void close();
}
