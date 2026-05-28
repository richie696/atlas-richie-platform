package com.richie.component.desensitize.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import com.richie.component.desensitize.core.support.SensitiveLogArg;
import com.richie.component.desensitize.core.util.DesensitizeUtils;
import org.slf4j.Marker;

/**
 * 在日志事件创建前将 {@link SensitiveLogArg} 参数就地替换为脱敏值。
 *
 * <p>这样即使使用常规 `%msg` pattern，也能输出脱敏后的参数。</p>
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public class SensitiveLogArgTurboFilter extends TurboFilter {

    @Override
    public FilterReply decide(
            Marker marker,
            Logger logger,
            Level level,
            String format,
            Object[] params,
            Throwable t) {
        if (params == null || params.length == 0) {
            return FilterReply.NEUTRAL;
        }
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            if (param instanceof SensitiveLogArg sensitive) {
                params[i] = maskSafely(sensitive);
            }
        }
        return FilterReply.NEUTRAL;
    }

    private static String maskSafely(SensitiveLogArg arg) {
        try {
            return DesensitizeUtils.mask(arg.value(), arg.type());
        } catch (IllegalStateException ignored) {
            return arg.value();
        }
    }
}

