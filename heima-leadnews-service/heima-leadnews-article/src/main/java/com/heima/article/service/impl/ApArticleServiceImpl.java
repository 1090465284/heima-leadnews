package com.heima.article.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.api.R;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.article.mapper.ApArticleConfigMapper;
import com.heima.article.mapper.ApArticleContentMapper;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.service.ApArticleService;
import com.heima.article.service.ArticleFreemarkerService;
import com.heima.common.constants.ArticleConstants;
import com.heima.file.service.FileStorageService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.pojos.ApArticleConfig;
import com.heima.model.article.pojos.ApArticleContent;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
@Slf4j
public class ApArticleServiceImpl extends ServiceImpl<ApArticleMapper, ApArticle> implements ApArticleService {

    @Autowired
    private ApArticleMapper apArticleMapper;

    private final static short MAX_PAGE_SIZE = 50;

    @Autowired
    private Configuration configuration;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private ApArticleContentMapper apArticleContentMapper;

    @Autowired
    private ApArticleConfigMapper apArticleConfigMapper;

    @Autowired
    private ArticleFreemarkerService articleFreemarkerService;

    @Override
    public ResponseResult load(ArticleHomeDto dto, Short type) {

        if(dto == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST);
        }
        Integer size = dto.getSize();
        if(size == null || size == 0){
            size = 10;
        }
        size = Math.min(size, MAX_PAGE_SIZE);
        size = 50;
        dto.setSize(size);

        if(!ArticleConstants.LOADTYPE_LOAD_MORE.equals(type) && !ArticleConstants.LOADTYPE_LOAD_NEW.equals(type)){
            type = ArticleConstants.LOADTYPE_LOAD_MORE;
        }

        if(StringUtils.isBlank(dto.getTag())){
            dto.setTag(ArticleConstants.DEFAULT_TAG);
        }

        if(dto.getMaxBehotTime() == null){
            dto.setMaxBehotTime(new Date());
        }
        if(dto.getMinBehotTime() == null){
            dto.setMinBehotTime(new Date());
        }


        List<ApArticle> apArticles = apArticleMapper.loadArticleList(dto, type);
        return ResponseResult.okResult(apArticles);
    }

    @Override
    public void test() throws IOException, TemplateException {
        //1.获取文章内容
        List<ApArticleContent> apArticleContents = apArticleContentMapper.selectAll();
        for(ApArticleContent apArticleContent : apArticleContents){
            if (apArticleContent != null && org.apache.commons.lang3.StringUtils.isNotBlank(apArticleContent.getContent()) && apArticleContent.getArticleId() != 1302862387124125698L) {
                //2.文章内容通过freemarker生成html文件
                StringWriter out = new StringWriter();
                Template template = configuration.getTemplate("article.ftl");

                Map<String, Object> params = new HashMap<>();
                params.put("content", JSONArray.parseArray(apArticleContent.getContent()));

                template.process(params, out);
                InputStream is = new ByteArrayInputStream(out.toString().getBytes());

                //3.把html文件上传到minio中
                String path = fileStorageService.uploadHtmlFile("", apArticleContent.getArticleId() + ".html", is);

                //4.修改ap_article表，保存static_url字段
                ApArticle article = new ApArticle();
                article.setId(apArticleContent.getArticleId());
                article.setStaticUrl(path);
                apArticleMapper.updateById(article);
            }
        }

    }

    @Override
    public ResponseResult saveArticle(ArticleDto articleDto) {
        //1.检查参数
        if(articleDto == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        ApArticle article = new ApArticle();
        BeanUtils.copyProperties(articleDto, article);

        if(articleDto.getId() == null){
            save(article);
            ApArticleConfig apArticleConfig = new ApArticleConfig(article.getId());
            apArticleConfigMapper.insert(apArticleConfig);
            ApArticleContent apArticleContent = new ApArticleContent();
            apArticleContent.setArticleId(article.getId());
            apArticleContent.setContent(articleDto.getContent());
            apArticleContentMapper.insert(apArticleContent);
        }else {
            updateById(article);
            LambdaQueryWrapper<ApArticleContent> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ApArticleContent::getArticleId, articleDto.getId());
            ApArticleContent apArticleContent = apArticleContentMapper.selectOne(queryWrapper);
            apArticleContent.setContent(articleDto.getContent());
            apArticleContentMapper.updateById(apArticleContent);
        }
        articleFreemarkerService.buildArticleToMinIO(article, articleDto.getContent());
        return ResponseResult.okResult(article.getId());
    }
}
