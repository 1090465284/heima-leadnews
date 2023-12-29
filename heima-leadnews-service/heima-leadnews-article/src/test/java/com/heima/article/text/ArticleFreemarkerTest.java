package com.heima.article.text;


import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.article.ArticleApplication;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.file.service.FileStorageService;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.pojos.ApArticleContent;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest(classes = ArticleApplication.class)
@RunWith(SpringRunner.class)
public class ArticleFreemarkerTest {
    @Test
    public void createStaticUrlTest() throws Exception {
        @Autowired
        private Configuration configuration;

        @Autowired
        private FileStorageService fileStorageService;


        @Autowired
        private ApArticleMapper apArticleMapper;

        @Autowired
        private ApArticleContentMapper apArticleContentMapper;

        //1.获取文章内容

        //2.文章内容通过freemarker生成html文件


        //3.把html文件上传到minio中


        //4.修改ap_article表，保存static_url字段

        }
    }
}
