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
package com.richie.context.utils.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Provider 抽象工厂（包级私有）
 * <p>
 * 集中管理所有算法 Provider 实例的创建与注册。
 * {@link CryptoUtils} 通过此类获取 Provider，不直接依赖具体实现类。
 * </p>
 *
 * @author richie696
 * @version 1.0
 * @since 2026/06/02
 */
final class ProviderFactory {

    private ProviderFactory() {
    }

    private static final Map<Algorithm, CryptoProvider> PROVIDERS = loadProviders();

    private static Map<Algorithm, CryptoProvider> loadProviders() {
        Map<Algorithm, CryptoProvider> map = new HashMap<>();
        map.put(Algorithm.AES, new AESProvider());
        map.put(Algorithm.RSA, new RSAProvider());
        map.put(Algorithm.ECDSA, new ECDSAProvider());
        map.put(Algorithm.ECDH, new ECDHProvider());
        map.put(Algorithm.DSA, new DSAProvider());
        map.put(Algorithm.SM2, new SM2Provider());
        map.put(Algorithm.SM4, new SM4Provider());
        return Collections.unmodifiableMap(map);
    }

    /**
     * 获取指定算法的 Provider
     *
     * @param algorithm 算法枚举
     * @return 算法 Provider
     * @throws IllegalArgumentException 如果算法未注册
     */
    static CryptoProvider provider(Algorithm algorithm) {
        CryptoProvider p = PROVIDERS.get(algorithm);
        if (p == null) {
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        }
        return p;
    }
}
