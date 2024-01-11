package com.heima.schedule.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.common.constants.RedisConstants;
import com.heima.common.constants.ScheduleConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.schedule.dtos.Task;
import com.heima.model.schedule.pojos.Taskinfo;
import com.heima.model.schedule.pojos.TaskinfoLogs;
import com.heima.schedule.ScheduleApplication;
import com.heima.schedule.mapper.TaskinfoLogsMapper;
import com.heima.schedule.mapper.TaskinfoMapper;
import com.heima.schedule.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;


@Service
@Transactional
@Slf4j
public class TaskServiceImpl implements TaskService {

    @Autowired
    private TaskinfoMapper taskinfoMapper;

    @Autowired
    private TaskinfoLogsMapper taskinfoLogsMapper;

    @Autowired
    private CacheService cacheService;

    @Override
    public long addTask(Task task) {
        //1.添加任务到数据库中
        boolean success = addTaskToDb(task);
        //2.添加任务到redis
        if(success){
            //如果任务的执行时间小于当前时间, 存入list
            //如果任务的施行时间大于当前时间, && 小于等于预设时间,存入zset中
            addTaskToCache(task);
        }

        return 0;
    }

    @Override
    public boolean cancelTask(long taskId) {
        boolean flag = false;

        //删除任务, 更新任务日志
        Task task = updateDB(taskId, ScheduleConstants.CANCELLED);
        //删除redis的数据
        if(task != null){
            removeTaskFromCache(task);
            flag = true;
        }
        return flag;
    }

    @Override
    public Task poll(int type, int priority) {
        Task task = null;
        try{
            String key = type + "_" + priority;
            //从redis中拉取数据
            String task_json = cacheService.lRightPop(RedisConstants.TASK_TOPIC + key);
            if(StringUtils.isNotBlank(task_json)){
                task = JSON.parseObject(task_json, Task.class);
                //修改数据库中的信息
                updateDB(task.getTaskId(), ScheduleConstants.EXECUTED);
            }
        }catch (Exception e){
            e.printStackTrace();
            log.error("poll task exception");
        }

        return task;
    }

    private void removeTaskFromCache(Task task) {
        String halfKey = task.getTaskType() + "_" + task.getPriority();
        if(task.getExecuteTime() <= System.currentTimeMillis()){
            cacheService.lRemove(RedisConstants.TASK_TOPIC + halfKey, 0, JSON.toJSONString(task));
        }else {
            cacheService.zRemove(RedisConstants.TASK_FUTURE + halfKey, JSON.toJSONString(task));
        }

    }

    private Task updateDB(long taskId, int status) {
        Task task = null;
        try {
            taskinfoMapper.deleteById(taskId);
            TaskinfoLogs taskinfoLogs = taskinfoLogsMapper.selectById(taskId);
            taskinfoLogs.setStatus(status);
            taskinfoLogsMapper.updateById(taskinfoLogs);

            task = new Task();
            BeanUtils.copyProperties(taskinfoLogs, task);
            task.setExecuteTime(taskinfoLogs.getExecuteTime().getTime());
        }catch (Exception e){
            log.error("task cancel exception taskId={}", taskId);
        }

        return task;
    }

    private void addTaskToCache(Task task) {
        String topicKey = RedisConstants.TASK_TOPIC + task.getTaskType() + "_" + task.getPriority();
        String futureKey = RedisConstants.TASK_FUTURE + task.getTaskType() + "_" + task.getPriority();
        //获取预设时间后的时间
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 5);
        long nextScheduleTimes = calendar.getTimeInMillis();

        if(task.getExecuteTime() <= System.currentTimeMillis()){
            cacheService.lLeftPush(topicKey, JSON.toJSONString(task));
        }else if(task.getExecuteTime() < nextScheduleTimes){
            cacheService.zAdd(futureKey, JSON.toJSONString(task), task.getExecuteTime());
        }
    }

    private boolean addTaskToDb(Task task) {

        boolean flag = false;

        try{
            Taskinfo taskinfo = new Taskinfo();
            BeanUtils.copyProperties(task, taskinfo);
            taskinfo.setExecuteTime(new Date(task.getExecuteTime()));
            taskinfoMapper.insert(taskinfo);

            TaskinfoLogs taskinfoLogs = new TaskinfoLogs();
            BeanUtils.copyProperties(taskinfo, taskinfoLogs);
            taskinfoLogs.setVersion(1);
            taskinfoLogs.setStatus(ScheduleConstants.SCHEDULED);
            taskinfoLogsMapper.insert(taskinfoLogs);
            task.setTaskId(taskinfo.getTaskId());
            flag = true;
        } catch (Exception e){
            e.printStackTrace();
        }
        return flag;
    }

    @Scheduled(cron = "0 */1 * * * ?")
    public void refresh(){

        log.info("未来数据定时刷新-----定时任务");

        Set<String> futureKeys = cacheService.scan(RedisConstants.TASK_FUTURE + "*");
        for(String futureKey : futureKeys){
            Set<String> tasks = cacheService.zRangeByScore(futureKey, 0, System.currentTimeMillis());
            String topicKey = RedisConstants.TASK_TOPIC + futureKey.split(RedisConstants.TASK_FUTURE)[1];
            if(!tasks.isEmpty()){
                cacheService.refreshWithPipeline(futureKey, topicKey, tasks);
                log.info("刷新成功");
            }
        }

    }

    /**
     * 数据库任务定时同步到redis中
     */
    @PostConstruct
    @Scheduled(cron = "0 */5 * * * ?")
    public void reloadData(){
        //清理缓存中的数据
        clearCache();
        //查询符合条件的任务 ,小于未来5分钟的数据
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 5);
        List<Taskinfo> taskInfoList = taskinfoMapper.selectList(Wrappers.<Taskinfo>lambdaQuery().lt(Taskinfo::getExecuteTime, calendar.getTime()));
        if(taskInfoList != null && taskInfoList.size() > 0){
            for(Taskinfo taskinfo : taskInfoList){
                Task task = new Task();
                BeanUtils.copyProperties(taskinfo, task);
                task.setExecuteTime(taskinfo.getExecuteTime().getTime());
                addTaskToCache(task);
            }
        }
        log.info("数据库的任务同步到了redis");
    }

    public void clearCache(){
        Set<String> topicKeys = cacheService.scan(ScheduleConstants.TOPIC + "*");
        Set<String> futureKeys = cacheService.scan(ScheduleConstants.FUTURE + "*");
        cacheService.delete(topicKeys);
        cacheService.delete(futureKeys);

    }
}
