package com.richie.component.mfa.mapper;

import com.richie.component.mfa.domain.UserInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户信息表 Mapper
 */
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {
}

