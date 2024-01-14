package com.heima.wemedia.service.impl;




import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.api.R;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.apis.article.IArticleClient;
import com.heima.common.constants.WemediaConstants;
import com.heima.common.constants.WmNewsMessageConstants;
import com.heima.common.exception.CustomException;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.NewsAuthDto;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.model.wemedia.pojos.WmMaterial;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmNewsMaterial;
import com.heima.model.wemedia.pojos.WmUser;
import com.heima.model.wemedia.vo.WmNewsVo;
import com.heima.utils.thread.WmThreadLocalUtil;
import com.heima.wemedia.mapper.WmMaterialMapper;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.mapper.WmNewsMaterialMapper;
import com.heima.wemedia.mapper.WmUserMapper;
import com.heima.wemedia.service.WmNewsAutoScanService;
import com.heima.wemedia.service.WmNewsService;
import com.heima.wemedia.service.WmNewsTaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class WmNewsServiceImpl  extends ServiceImpl<WmNewsMapper, WmNews> implements WmNewsService {

    @Autowired
    private WmNewsMaterialMapper wmNewsMaterialMapper;

    @Autowired
    private WmMaterialMapper wmMaterialMapper;

    @Autowired
    private WmNewsAutoScanService wmNewsAutoScanService;

    @Autowired
    private WmNewsTaskService wmNewsTaskService;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private IArticleClient articleClient;

    @Autowired
    private WmNewsMapper wmNewsMapper;



    @Override
    public ResponseResult findList(WmNewsPageReqDto dto) {
        dto.checkParam();
        IPage page = new Page(dto.getPage(), dto.getSize());

        LambdaQueryWrapper<WmNews> lambdaQueryWrapper = new LambdaQueryWrapper<>();

        //状态查询
        if(dto.getStatus() != null){
            lambdaQueryWrapper.eq(WmNews::getStatus, dto.getStatus());
        }
        //频道查询
        if(dto.getChannelId() != null){
            lambdaQueryWrapper.eq(WmNews::getChannelId, dto.getChannelId());
        }

        //时间查询
        if(dto.getBeginPubDate()!= null && dto.getEndPubDate()!= null){
            lambdaQueryWrapper.between(WmNews::getPublishTime, dto.getBeginPubDate(), dto.getEndPubDate());
        }

        //关键字模糊查询
        if(StringUtils.isNotBlank(dto.getKeyword())){
            lambdaQueryWrapper.like(WmNews::getTitle, dto.getKeyword());
        }

        //查询登录人的文章
        lambdaQueryWrapper.eq(WmNews::getUserId, WmThreadLocalUtil.getUser().getId());

        //时间倒序排序
        lambdaQueryWrapper.orderByDesc(WmNews::getPublishTime);

        page = page(page, lambdaQueryWrapper);

        ResponseResult responseResult = new PageResponseResult(dto.getPage(), dto.getSize(), (int)page.getTotal());
        responseResult.setData(page.getRecords());
        return responseResult;
    }

    @Override
    public ResponseResult submitNews(WmNewsDto dto) {
        if(dto == null || dto.getContent() == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        if(dto.getPublishTime() == null){
            dto.setPublishTime(new Date());
        }
        WmNews wmNews = new WmNews();
        BeanUtils.copyProperties(dto, wmNews);
        if(dto.getImages() != null && dto.getImages().size() > 0){
            String imageStr = StringUtils.join(dto.getImages(), ",");
            wmNews.setImages(imageStr);
        }
        //封面类型为自动
        if(dto.getType().equals(WemediaConstants.WM_NEWS_TYPE_AUTO)){
            wmNews.setType(null);
        }
        saveOrUpdateWmNews(wmNews);

        if(dto.getStatus().equals(WmNews.Status.NORMAL.getCode())){
            return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
        }
        //保存图片和文章内容的关系
        List<String> materials = extractUrlInfo(dto.getContent());
        saveRelativeInfoForContent(wmNews.getId(), materials);

        //保存封面和素材的关系,并自动匹配封面
        saveRelativeInfoForCover(dto, wmNews, materials);

        //审核文章
//        wmNewsAutoScanService.autoScanWmNews(wmNews.getId());
        wmNewsTaskService.addNewToTask(wmNews.getId(), wmNews.getPublishTime());
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    @Override
    public ResponseResult downOrUp(WmNewsDto dto) {
        //1.检查参数
        if(dto.getId() == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.查询文章
        WmNews wmNews = getById(dto.getId());
        //3.判断文章是否已发布
        if(wmNews == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST, "文章不存在");
        }
        if(!wmNews.getStatus().equals(WmNews.Status.PUBLISHED.getCode())){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID, "当前文章不是发布状态");
        }
        //4.修改文章enable
        if(dto.getEnable() != null && dto.getEnable() > -1 && dto.getEnable() < 2){
            if(wmNews.getArticleId() != null){
                Map<String, Object> map = new HashMap<>();
                map.put("articleId", wmNews.getArticleId());
                map.put("enable", dto.getEnable());
//                kafkaTemplate.send(WmNewsMessageConstants.WM_NEWS_UP_OR_DOWN_TOPIC, JSON.toJSONString(map));
                ResponseResult responseResult = articleClient.updateByMap(map);
                if(!responseResult.getCode().equals(200)){
                    throw new RuntimeException("WmNewsServiceImpl-修改失败");
                }
            }
            update(Wrappers.<WmNews>lambdaUpdate().set(WmNews::getEnable, dto.getEnable()).eq(WmNews::getId, wmNews.getId()));
        }

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    private void saveRelativeInfoForCover(WmNewsDto dto, WmNews wmNews, List<String> materials) {
        List<String> images = dto.getImages();
        if(dto.getType().equals(WemediaConstants.WM_NEWS_TYPE_AUTO)){
            if(materials.size() >= 3){
                wmNews.setType(WemediaConstants.WM_NEWS_MANY_IMAGE);
                images = materials.stream().limit(3).collect(Collectors.toList());
            }else if(materials.size() >= 1){
                wmNews.setType(WemediaConstants.WM_NEWS_SINGLE_IMAGE);
                images = materials.stream().limit(3).collect(Collectors.toList());
            }else {
                wmNews.setType(WemediaConstants.WM_NEWS_NONE_IMAGE);
            }
            if(images != null && images.size() > 0){
                wmNews.setImages(StringUtils.join(images, ","));
            }
            updateById(wmNews);
        }

        if(images != null && images.size() > 0){
            saveRelativeInfo(wmNews.getId(), images, WemediaConstants.WM_COVER_REFERENCE);
        }

    }

    private void saveRelativeInfoForContent(Integer id, List<String> materials) {
        saveRelativeInfo(id, materials, WemediaConstants.WM_CONTENT_REFERENCE);
    }

    private void saveRelativeInfo(Integer id, List<String> materials, Short type) {
        if(materials == null || materials.size() == 0){
            return ;
        }
        List<WmMaterial> wmMaterials = wmMaterialMapper.selectList(Wrappers.<WmMaterial>lambdaQuery().in(WmMaterial::getUrl, materials));
        if(wmMaterials == null ||wmMaterials.size() != materials.size()){
            throw new CustomException(AppHttpCodeEnum.MATERIAL_REFERENCE_FAIL);
        }
        List<Integer> idList = wmMaterials.stream().map(WmMaterial::getId).collect(Collectors.toList());

        wmNewsMaterialMapper.saveRelations(idList, id, type);
    }


    private List<String> extractUrlInfo(String content) {
        List<Map> maps = JSON.parseArray(content, Map.class);
        List<String> urls = new ArrayList<>();
        for(Map map : maps){
            if(map.get("type").equals(WemediaConstants.WM_NEWS_TYPE_IMAGE)){
                urls.add((String) map.get("value"));
            }
        }
        return urls;
    }

    private void saveOrUpdateWmNews(WmNews wmNews) {
        //补全属性
        wmNews.setUserId(WmThreadLocalUtil.getUser().getId());
        wmNews.setSubmitedTime(new Date());
        wmNews.setEnable((short)1);
        if(wmNews.getId() == null){
            wmNews.setCreatedTime(new Date());
            save(wmNews);
        }else {
            wmNewsMaterialMapper.delete(Wrappers.<WmNewsMaterial>lambdaQuery().eq(WmNewsMaterial::getNewsId, wmNews.getId()));
            updateById(wmNews);
        }
    }

    /**
     * 查询文章列表
     * @param dto
     * @return
     */
    @Override
    public ResponseResult findList(NewsAuthDto dto) {
        //1.参数检查
        dto.checkParam();

        //记录当前页
        int currentPage = dto.getPage();

        //2.分页查询+count查询
        dto.setPage((dto.getPage()-1)*dto.getSize());
        List<WmNewsVo> wmNewsVoList = wmNewsMapper.findListAndPage(dto);
        int count = wmNewsMapper.findListCount(dto);

        //3.结果返回
        ResponseResult responseResult = new PageResponseResult(currentPage,dto.getSize(),count);
        responseResult.setData(wmNewsVoList);
        return responseResult;
    }

    @Autowired
    private WmUserMapper wmUserMapper;

    /**
     * 查询文章详情
     * @param id
     * @return
     */
    @Override
    public ResponseResult findWmNewsVo(Integer id) {
        //1.检查参数
        if(id == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.查询文章信息
        WmNews wmNews = getById(id);
        if(wmNews == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST);
        }

        //3.查询用户信息
        WmUser wmUser = wmUserMapper.selectById(wmNews.getUserId());

        //4.封装vo返回
        WmNewsVo vo = new WmNewsVo();
        //属性拷贝
        BeanUtils.copyProperties(wmNews,vo);
        if(wmUser != null){
            vo.setAuthorName(wmUser.getName());
        }

        ResponseResult responseResult = new ResponseResult().ok(vo);

        return responseResult;
    }

    /**
     * 文章审核，修改状态
     * @param status 2  审核失败  4 审核成功
     * @param dto
     * @return
     */
    @Override
    public ResponseResult updateStatus(Short status, NewsAuthDto dto) {
        //1.检查参数
        if(dto == null || dto.getId() == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.查询文章信息
        WmNews wmNews = getById(dto.getId());
        if(wmNews == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST);
        }

        //3.修改文章的状态
        wmNews.setStatus(status);
        if(StringUtils.isNotBlank(dto.getMsg())){
            wmNews.setReason(dto.getMsg());
        }
        updateById(wmNews);

        //审核成功，则需要创建app端文章数据，并修改自媒体文章
        if(status.equals(WemediaConstants.WM_NEWS_AUTH_PASS)){
            //创建app端文章数据
            ResponseResult responseResult = wmNewsAutoScanService.saveAppArticle(wmNews);
            if(responseResult.getCode().equals(200)){
                wmNews.setArticleId((Long) responseResult.getData());
                wmNews.setStatus(WmNews.Status.PUBLISHED.getCode());
                updateById(wmNews);
            }
        }

        //4.返回
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

}
