package com.richie.component.dao.spy;

import com.p6spy.engine.spy.appender.CustomLineFormat;

/**
 * 优化后的单行SQL输出
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-24 16:15:35
 */
public class OptimizeLineFormat extends CustomLineFormat {
    @Override
    public String formatMessage(int connectionId, String now, long elapsed, String category, String prepared, String sql, String url) {
        sql = sql.replaceAll("\\n", "").replaceAll("\\s+", " ");
        return super.formatMessage(connectionId, now, elapsed, category, prepared, sql, url);
    }
}
