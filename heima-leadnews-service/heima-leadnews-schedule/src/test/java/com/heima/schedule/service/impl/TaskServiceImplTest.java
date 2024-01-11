package com.heima.schedule.service.impl;

import com.heima.common.constants.RedisConstants;
import com.heima.common.constants.ScheduleConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.schedule.dtos.Task;
import com.heima.schedule.ScheduleApplication;
import com.heima.schedule.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ScheduleApplication.class)
@RunWith(SpringRunner.class)
@Slf4j

class TaskServiceImplTest {
    @Autowired
    private TaskService taskService;

    @Autowired
    private CacheService cacheService;


    public void addTask(Integer type, Long time){
        Task task = new Task();
        task.setTaskType(type);
        task.setPriority(50);
        task.setParameters("task test".getBytes(StandardCharsets.UTF_8));
        task.setExecuteTime(time);

        long taskId = taskService.addTask(task);
        System.out.println(taskId);
    }

    @Test
    public void cancelTask(){
        taskService.cancelTask(1745293317320884225L);
    }

    @Test
    public void testPoll(){
        Task task = taskService.poll(100, 50);
        System.out.println(task);
    }


    @Test
    public void testKeys(){
        Set<String> scan = cacheService.scan(RedisConstants.TASK_FUTURE + "*");
        System.out.println(scan);
    }

    /**
     * 未来定时刷新
     */


    @Test
    public void addTaskBatch(){
        for (int i = 0; i < 5; i ++){
            addTask(100 + i, new Date().getTime() + 600 * i);
        }
    }
}