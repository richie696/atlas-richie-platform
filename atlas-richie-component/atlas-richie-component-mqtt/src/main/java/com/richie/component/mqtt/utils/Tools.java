/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mqtt.utils;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * 签名工具类
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-09 14:57:22
 */
public class Tools {

    private Tools() {
    }

    /**
     * 阿里云签名方法
     *
     * @param text      要签名的文本
     * @param secretKey 阿里云MQ secretKey
     * @return 加密后的字符串
     * @throws InvalidKeyException      当密钥无效的时候抛出该异常
     * @throws NoSuchAlgorithmException 当算法不匹配时抛出该异常
     */
    public static String macSignature(String text,
                                      String secretKey) throws InvalidKeyException, NoSuchAlgorithmException {
        var charset = StandardCharsets.UTF_8;
        var algorithm = "HmacSHA1";
        var mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(secretKey.getBytes(charset), algorithm));
        var bytes = mac.doFinal(text.getBytes(charset));
        return new String(Base64.encodeBase64(bytes), charset);
    }

}
