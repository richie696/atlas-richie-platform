package com.richie.component.mfa.service.impl;

import com.richie.component.mfa.domain.UserInfo;
import com.richie.component.mfa.mapper.UserInfoMapper;
import com.richie.component.mfa.service.UserInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

/**
 * 用户信息服务实现
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {

    @Override
    public UserInfo findByUsername(String username) {
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInfo::getUsername, username).last("LIMIT 1");
        return this.getOne(wrapper, false);
    }

    @Override
    public boolean save(UserInfo entity) {
        ZonedDateTime now = ZonedDateTime.now();
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(now.toInstant());
        }
        entity.setUpdateTime(now.toInstant());
        return super.save(entity);
    }
}

