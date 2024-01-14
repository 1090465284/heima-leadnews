package com.heima.wemedia.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.dtos.NewsAuthDto;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.model.wemedia.pojos.WmNews;

public interface WmNewsService extends IService<WmNews> {


    ResponseResult findList(WmNewsPageReqDto dto);

    ResponseResult submitNews(WmNewsDto dto);

    ResponseResult downOrUp(WmNewsDto dto);



    /**
     * 查询文章列表
     * @param dto
     * @return
     */
    public ResponseResult findList(NewsAuthDto dto);

    /**
     * 查询文章详情
     * @param id
     * @return
     */
    public ResponseResult findWmNewsVo(Integer id);

    /**
     * 文章审核，修改状态
     * @param status  2  审核失败  4 审核成功
     * @param dto
     * @return
     */
    public ResponseResult updateStatus(Short status ,NewsAuthDto dto);
}
