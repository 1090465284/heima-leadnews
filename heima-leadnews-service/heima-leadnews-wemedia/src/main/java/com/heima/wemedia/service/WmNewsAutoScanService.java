package com.heima.wemedia.service;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.pojos.WmNews;

public interface WmNewsAutoScanService {
    public void autoScanWmNews(Integer id);

    ResponseResult saveAppArticle(WmNews wmNews);
}
