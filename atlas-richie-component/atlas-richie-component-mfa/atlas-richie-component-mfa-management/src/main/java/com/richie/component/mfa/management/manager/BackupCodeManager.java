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
package com.richie.component.mfa.management.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * 备份码管理器
 * <p>
 * 职责：生成、哈希和验证备份码
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
public class BackupCodeManager {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 生成备份码
     * <p>
     * 使用 SecureRandom 生成指定数量的8位数字备份码
     * <p>
     * 备份码格式：8位数字，例如 "12345678"
     *
     * @param count 生成数量（必填，通常为 8-10 个）
     * @return 备份码列表（明文，8位数字字符串）
     */
    public List<String> generateBackupCodes(int count) {
        SecureRandom random = new SecureRandom();
        List<String> codes = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            // 生成8位数字备份码
            int code = random.nextInt(100000000);
            codes.add("%08d".formatted(code));
        }

        return codes;
    }

    /**
     * 哈希备份码
     * <p>
     * 使用 BCrypt 算法对备份码进行哈希，用于安全存储
     * <p>
     * 注意：哈希后的备份码存储在数据库中，明文备份码仅在绑定接口返回一次
     *
     * @param plainCodes 明文备份码列表（必填）
     * @return 哈希后的备份码列表（用于存储到数据库）
     */
    public List<String> hashBackupCodes(List<String> plainCodes) {
        List<String> hashedCodes = new ArrayList<>();
        for (String code : plainCodes) {
            hashedCodes.add(passwordEncoder.encode(code));
        }
        return hashedCodes;
    }

    /**
     * 验证备份码
     * <p>
     * 使用 BCrypt 算法验证明文备份码是否与哈希后的备份码匹配
     *
     * @param plainCode  明文备份码（用户输入的备份码）
     * @param hashedCode 哈希后的备份码（从数据库读取）
     * @return 是否匹配
     * <ul>
     *   <li>{@code true}：备份码正确</li>
     *   <li>{@code false}：备份码错误</li>
     * </ul>
     */
    public boolean verifyBackupCode(String plainCode, String hashedCode) {
        return passwordEncoder.matches(plainCode, hashedCode);
    }
}
