package com.heima.article.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.common.dtos.ResponseResult;
import freemarker.template.TemplateException;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.IOException;

public interface ApArticleService extends IService<ApArticle> {
    public ResponseResult load(ArticleHomeDto dto, Short type);

    void test() throws IOException, TemplateException;

    public ResponseResult saveArticle(@RequestBody ArticleDto articleDto);

}
