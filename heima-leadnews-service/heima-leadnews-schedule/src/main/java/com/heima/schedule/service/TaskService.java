package com.heima.schedule.service;

import com.heima.model.schedule.dtos.Task;

public interface TaskService {

    long addTask(Task task);

    boolean cancelTask(long taskId);

    Task poll(int type, int priority);
}
