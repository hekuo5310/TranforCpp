package com.github.tranforcpp.utils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Folia兼容工具类
 * <p>
 * 提供与Folia服务器核心的兼容性支持。
 * 封装了异步任务调度相关的API。
 * <p>
 * 主要功能：
 * - 异步定时任务调度
 * - 线程管理
 * - 兼容性适配
 */
public class FoliaCompatUtil {

    public static TaskHandle runAsyncTaskTimer(Runnable task, long delay, long period, TimeUnit timeUnit) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FoliaCompat-AsyncTask");
            t.setDaemon(true);
            return t;
        });
        
        scheduler.scheduleWithFixedDelay(task, delay, period, timeUnit);
        return new ExecutorTaskHandle(scheduler);
    }

    public interface TaskHandle {
        void cancel();
    }

        private record ExecutorTaskHandle(ScheduledExecutorService scheduler) implements TaskHandle {

        @Override
            public void cancel() {
                if (scheduler != null && !scheduler.isShutdown()) {
                    scheduler.shutdown();
                    try {
                        if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                            scheduler.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        scheduler.shutdownNow();
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
}