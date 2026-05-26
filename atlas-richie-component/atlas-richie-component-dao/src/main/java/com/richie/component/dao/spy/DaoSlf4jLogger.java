package com.richie.component.dao.spy;

import com.richie.component.dao.config.DaoPropertiesHolder;
import com.p6spy.engine.logging.Category;
import com.p6spy.engine.spy.appender.Slf4JLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * p6spy 的 SLF4J 日志实现，根据 DaoProperties 的 enableLogging 控制是否输出 SQL 日志。
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-24
 */
public class DaoSlf4jLogger extends Slf4JLogger {

    /** p6spy 使用的 SLF4J 日志 */
    private final Logger log;

    public DaoSlf4jLogger() {
        log = LoggerFactory.getLogger("p6spy");
    }

    @Override
    public void logText(String text) {
        if (DaoPropertiesHolder.getProperties().isEnableLogging()) {
            super.logText(text);
        }
    }

    @Override
    public void logSQL(int connectionId, String now, long elapsed, Category category, String prepared, String sql, String url) {
        final String msg = strategy.formatMessage(connectionId, now, elapsed,
                category.toString(), prepared, sql, url);
        if (Category.ERROR.equals(category)) {
            log.error(msg);
        } else if (Category.WARN.equals(category)) {
            log.warn(msg);
        } else if (Category.DEBUG.equals(category) && DaoPropertiesHolder.getProperties().isEnableLogging()) {
            log.debug(msg);
        } else {
            if (DaoPropertiesHolder.getProperties().isEnableLogging()) {
                log.info(msg);
            }
        }
    }

    @Override
    public boolean isCategoryEnabled(Category category) {
        return super.isCategoryEnabled(category);
    }
}
