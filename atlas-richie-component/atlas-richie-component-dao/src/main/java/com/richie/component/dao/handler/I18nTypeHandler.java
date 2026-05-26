package com.richie.component.dao.handler;

import com.richie.component.i18n.handle.I18nHandle;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.springframework.context.i18n.LocaleContextHolder;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis 国际化类型处理器：将库中存储的字典 key 按当前语言解析为对应文案后返回。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-09
 */
public class I18nTypeHandler implements TypeHandler<String> {
    @Override
    public void setParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter);
    }

    @Override
    public String getResult(ResultSet rs, String columnName) throws SQLException {
        if (rs.getString(columnName) == null) {
            return "";
        }
        return I18nHandle.getI18nDictionary(rs.getString(columnName), LocaleContextHolder.getLocale().toString());
    }

    @Override
    public String getResult(ResultSet rs, int columnIndex) throws SQLException {

        if (rs.getString(columnIndex) == null) {
            return "";
        }
        return I18nHandle.getI18nDictionary(rs.getString(columnIndex), LocaleContextHolder.getLocale().toString());
    }

    @Override
    public String getResult(CallableStatement cs, int columnIndex) throws SQLException {
        if (cs.getString(columnIndex) == null) {
            return "";
        }
        return I18nHandle.getI18nDictionary(cs.getString(columnIndex), LocaleContextHolder.getLocale().toString());
    }
}
