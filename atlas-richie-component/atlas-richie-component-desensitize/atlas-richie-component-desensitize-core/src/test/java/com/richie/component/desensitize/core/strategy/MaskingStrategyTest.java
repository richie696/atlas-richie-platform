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
}
