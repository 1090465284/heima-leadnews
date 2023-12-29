package com.heima.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.user.dtos.LoginDto;
import com.heima.model.user.pojos.ApUser;
import com.heima.user.mapper.ApUserMapper;
import com.heima.user.service.ApUserService;

import com.heima.utils.common.AppJwtUtil;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Handler;


@Service
public class ApUserServiceImpl extends ServiceImpl<ApUserMapper, ApUser> implements ApUserService {
    @Override
    public ResponseResult login(LoginDto dto) {
        //注册用户登录
        if(StringUtils.isNotBlank(dto.getPhone()) && StringUtils.isNotBlank(dto.getPassword())){
            LambdaQueryWrapper<ApUser> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ApUser::getPhone, dto.getPhone());
            ApUser user = getOne(queryWrapper);
            if(user == null){
                return ResponseResult.errorResult(AppHttpCodeEnum.AP_USER_DATA_NOT_EXIST);
            }
            String password = user.getPassword();
            String salt = user.getSalt();
            String md5Password = DigestUtils.md5DigestAsHex((dto.getPassword() + salt).getBytes());
            if(!md5Password.equals(password)){
                return ResponseResult.errorResult(AppHttpCodeEnum.LOGIN_PASSWORD_ERROR);
            }
            String token = AppJwtUtil.getToken(user.getId().longValue());
            Map<String, Object> map = new HashMap<>();
            map.put("token", token);
            user.setPassword(null);
            user.setSalt(null);
            map.put("user", user);
            return ResponseResult.okResult(map);
        }else{
            //游客登录
            String token = AppJwtUtil.getToken(0L);
            Map<String, Object> map = new HashMap<>();
            map.put("token", token);
            return ResponseResult.okResult(map);
        }
    }
}
