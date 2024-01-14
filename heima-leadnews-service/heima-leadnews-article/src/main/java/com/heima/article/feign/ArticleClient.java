package com.heima.article.feign;

import com.baomidou.mybatisplus.extension.api.R;
import com.heima.apis.article.IArticleClient;
import com.heima.article.service.ApArticleConfigService;
import com.heima.article.service.ApArticleService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import org.apache.zookeeper.KeeperException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
public class ArticleClient implements IArticleClient {

    @Autowired
    private ApArticleService apArticleService;

    @Autowired
    private ApArticleConfigService apArticleConfigService;
    @PostMapping("/api/v1/article/save")
    @Override
    public ResponseResult saveArticle(ArticleDto articleDto) {
        return apArticleService.saveArticle(articleDto);
    }

    @PostMapping("/api/v1/article/updateByMap")
    public ResponseResult updateByMap(Map map){
        apArticleConfigService.updateByMap(map);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
