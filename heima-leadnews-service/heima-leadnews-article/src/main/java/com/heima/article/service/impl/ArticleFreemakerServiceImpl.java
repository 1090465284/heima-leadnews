package com.heima.article.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.article.mapper.ApArticleContentMapper;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.service.ArticleFreemarkerService;
import com.heima.file.service.FileStorageService;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.pojos.ApArticleContent;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;


@Service
public class ArticleFreemakerServiceImpl implements ArticleFreemarkerService {


    @Autowired
    private Configuration configuration;

    @Autowired
    private FileStorageService fileStorageService;


    @Autowired
    private ApArticleMapper apArticleMapper;

    @Autowired
    private ApArticleContentMapper apArticleContentMapper;

    /**
     * 生成静态文件上传到minio中
     * @param article
     * @param content
     */
    @Async
    @Override
    public void buildArticleToMinIO(ApArticle article, String content) {
        if (StringUtils.isNotBlank(content)) {
            //2.文章内容通过freemarker生成html文件
            StringWriter out = new StringWriter();
            Template template = null;
            try {
                template = configuration.getTemplate("article.ftl");
            } catch (IOException e) {
                e.printStackTrace();
            }

            Map<String, Object> params = new HashMap<>();
            params.put("content", JSONArray.parseArray(content));

            try {
                template.process(params, out);
            } catch (TemplateException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            InputStream is = new ByteArrayInputStream(out.toString().getBytes());

            //3.把html文件上传到minio中
            String path = fileStorageService.uploadHtmlFile("", article.getId() + ".html", is);

            //4.修改ap_article表，保存static_url字段
            article.setStaticUrl(path);
            apArticleMapper.updateById(article);
        }
    }
}
