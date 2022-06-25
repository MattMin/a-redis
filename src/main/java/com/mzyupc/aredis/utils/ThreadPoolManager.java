package com.mzyupc.aredis.utils;

import java.util.concurrent.*;

/**
 * 线程池
 *
 * @author mzyupc@163.com
 * @date 2022/4/16 15:10
 */
public class ThreadPoolManager {

    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            20,
            100,
            10,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(),
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy()
    );

    public static Future<?> submit (Runnable task) {
       return EXECUTOR.submit(task);
    }

    public static void execute(Runnable command) {
        EXECUTOR.execute(command);
    }

    public static ThreadPoolExecutor getExecutor() {
        return EXECUTOR;
    }
}
