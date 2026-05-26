package com.richie.component.threadpool.mapper;

import com.richie.component.threadpool.domain.ApiInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * API 数据访问接口
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-09 11:07:20
 */
@Mapper
public interface ApiMapper extends BaseMapper<ApiInfo> {

    /**
     * 根据条件查询
     *
     * @param apiInfo 查询条件
     * @return 返回查询结果
     */
    Page<ApiInfo> selectByCondition(@Param("condition") ApiInfo apiInfo, Page<ApiInfo> page);

}
