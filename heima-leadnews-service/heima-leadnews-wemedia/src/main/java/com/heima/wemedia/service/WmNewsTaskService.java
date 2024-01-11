package com.heima.wemedia.service;

import javax.xml.crypto.Data;
import java.util.Date;

public interface WmNewsTaskService {
    /**
     * 添加任务到延迟队列中
     * @param id
     * @param publishTime 任务的执行时间
     */

    public void addNewToTask(Integer id, Date publishTime);


    /**
     * 消费任务
     */
    public void scanNewsByTask();

}
