package com.richie.component.logging.mapper;

import com.richie.component.logging.domain.AccessLogInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 访问日志 MyBatis Mapper，对应表 access_log_info。
 *
 * @author richie696
 * @since 2022-10-09
 */
@Mapper
public interface AccessLogMapper extends BaseMapper<AccessLogInfo> {
}
