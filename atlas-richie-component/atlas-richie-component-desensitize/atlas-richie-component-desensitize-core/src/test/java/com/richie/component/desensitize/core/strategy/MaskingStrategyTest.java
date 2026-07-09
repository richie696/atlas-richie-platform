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
package com.richie.component.desensitize.core.strategy;

import com.richie.component.desensitize.core.model.MaskRule;
import com.richie.component.desensitize.core.model.MaskType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * MaskingStrategyTest 测试类。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
class MaskingStrategyTest {

    @Test
    void phoneMaskKeepsHeadAndTail() {
        PhoneMaskingStrategy strategy = new PhoneMaskingStrategy();
        String masked = strategy.mask("13812348000", MaskRule.of(MaskType.PHONE, '*'));
        assertEquals("138****8000", masked);
    }

    @Test
    void phoneMaskShortValue() {
        PhoneMaskingStrategy strategy = new PhoneMaskingStrategy();
        String masked = strategy.mask("138", MaskRule.of(MaskType.PHONE, '*'));
        assertEquals("***", masked);
    }

    @Test
    void idCardMask() {
        IdCardMaskingStrategy strategy = new IdCardMaskingStrategy();
        String masked = strategy.mask("110101199001011234", MaskRule.of(MaskType.ID_CARD, '*'));
        assertEquals("110101********1234", masked);
    }

    @Test
    void emailMask() {
        EmailMaskingStrategy strategy = new EmailMaskingStrategy();
        String masked = strategy.mask("user@example.com", MaskRule.of(MaskType.EMAIL, '*'));
        assertEquals("u***@example.com", masked);
    }

    @Test
    void bankCardMask() {
        BankCardMaskingStrategy strategy = new BankCardMaskingStrategy();
        String masked = strategy.mask("6222021234567890", MaskRule.of(MaskType.BANK_CARD, '*'));
        assertTrue(masked.contains("6222"));
        assertTrue(masked.contains("7890"));
        assertTrue(masked.contains("****"));
    }

    @Test
    void nameMask() {
        NameMaskingStrategy strategy = new NameMaskingStrategy();
        assertEquals("张**", strategy.mask("张三丰", MaskRule.of(MaskType.NAME, '*')));
    }

    @Test
    void passwordMask() {
        PasswordMaskingStrategy strategy = new PasswordMaskingStrategy();
        String masked = strategy.mask("secret", MaskRule.of(MaskType.PASSWORD, '*'));
        assertEquals("******", masked);
    }

    @Test
    void nullAndEmptyPassThrough() {
        PhoneMaskingStrategy strategy = new PhoneMaskingStrategy();
        assertNull(strategy.mask(null, MaskRule.of(MaskType.PHONE, '*')));
        assertEquals("", strategy.mask("", MaskRule.of(MaskType.PHONE, '*')));
    }

    @Test
    void emailWithoutAtMasksAsShortValue() {
        EmailMaskingStrategy strategy = new EmailMaskingStrategy();
        assertEquals("***", strategy.mask("bad", MaskRule.of(MaskType.EMAIL, '*')));
    }

    @Test
    void emailSingleCharLocalPart() {
        EmailMaskingStrategy strategy = new EmailMaskingStrategy();
        assertEquals("a***@example.com", strategy.mask("a@example.com", MaskRule.of(MaskType.EMAIL, '*')));
    }

    @Test
    void addressMaskKeepsPrefix() {
        AddressMaskingStrategy strategy = new AddressMaskingStrategy();
        String masked = strategy.mask("北京市朝阳区某某路1号", MaskRule.of(MaskType.ADDRESS, '*'));
        assertTrue(masked.startsWith("北京市"));
        assertTrue(masked.contains("*"));
    }

    @Test
    void bankCardShortValueFullyMasked() {
        BankCardMaskingStrategy strategy = new BankCardMaskingStrategy();
        assertEquals("***", strategy.mask("622", MaskRule.of(MaskType.BANK_CARD, '*')));
    }

    @Test
    void nameSingleChar() {
        NameMaskingStrategy strategy = new NameMaskingStrategy();
        assertEquals("*", strategy.mask("张", MaskRule.of(MaskType.NAME, '*')));
    }
}
