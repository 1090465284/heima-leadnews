package com.heima.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heima.common.constants.ArticleConstants;
import com.heima.model.article.pojos.ApArticleContent;

import java.util.List;

public interface ApArticleContentMapper extends BaseMapper<ApArticleContent> {
    List<ApArticleContent> selectAll();
}
