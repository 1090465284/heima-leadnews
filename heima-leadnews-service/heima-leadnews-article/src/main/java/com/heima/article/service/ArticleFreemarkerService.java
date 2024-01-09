package com.heima.article.service;

import com.heima.model.article.pojos.ApArticle;
import org.springframework.boot.ApplicationRunner;

public interface ArticleFreemarkerService {
    public void buildArticleToMinIO(ApArticle article, String Content);
}
