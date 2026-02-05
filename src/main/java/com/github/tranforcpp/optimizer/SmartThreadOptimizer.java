package com.github.tranforcpp.optimizer;

import com.github.tranforcpp.TranforCPlusPlus;
import org.bukkit.Bukkit;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 智能线程优化器
 * <p>
 * 动态调整线程池大小以适应服务器负载变化。
 * 监控服务器TPS和线程使用情况，自动优化线程资源配置。
 * <p>
 * 主要特性：
 * - 动态线程池调整
 * - 服务器性能监控
 * - 自适应优化算法
 * - 资源使用统计
 */
public class SmartThreadOptimizer {
    
    private final TranforCPlusPlus plugin;
    private final ThreadMXBean threadBean;

    private final AtomicLong totalTaskCount = new AtomicLong(0);
    private final AtomicLong completedTaskCount = new AtomicLong(0);
    private final AtomicInteger activeThreadCount = new AtomicInteger(0);
    private final AtomicLong lastAdjustmentTime = new AtomicLong(System.currentTimeMillis());

    private ThreadPoolExecutor dynamicThreadPool;
    private ScheduledExecutorService monitoringService;

    private static final int MIN_THREADS = 2;
    private static final int MAX_THREADS = 32;
    private static final long MONITORING_INTERVAL = 5000L;
    private static final long ADJUSTMENT_COOLDOWN = 30000L;
    
    public SmartThreadOptimizer(TranforCPlusPlus plugin) {
        this.plugin = plugin;
        this.threadBean = ManagementFactory.getThreadMXBean();
    }
    public void initialize() {
        createDynamicThreadPool();
        startMonitoringServices();
    }
    private void createDynamicThreadPool() {
        int initialThreads = calculateInitialThreadCount();
        
        dynamicThreadPool = new ThreadPoolExecutor(
            initialThreads,
            Math.max(initialThreads * 2, MAX_THREADS),
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
                new SmartThreadFactory(),
            new SmartRejectedExecutionHandler()
        );
        
        dynamicThreadPool.allowCoreThreadTimeOut(true);
    }

    private int calculateInitialThreadCount() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int memoryMB = (int) (Runtime.getRuntime().maxMemory() / (1024 * 1024));

        int cpuBased = Math.max(MIN_THREADS, Math.min(availableProcessors, 8));
        int memoryBased = Math.max(MIN_THREADS, Math.min(memoryMB / 512, 16));

        return Math.min(cpuBased, memoryBased);
    }
    private void startMonitoringServices() {
        monitoringService = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "SmartThreadOptimizer-Monitor");
            t.setDaemon(true);
            return t;
        });
        
        monitoringService.scheduleAtFixedRate(
            this::performAdaptiveOptimization,
            MONITORING_INTERVAL,
            MONITORING_INTERVAL,
            TimeUnit.MILLISECONDS
        );


        monitoringService.scheduleAtFixedRate(
            this::collectBukkitMetrics,
            100L,
            200L,
            TimeUnit.MILLISECONDS
        );
    }
    private void performAdaptiveOptimization() {
        try {
            long currentTime = System.currentTimeMillis();

            if (currentTime - lastAdjustmentTime.get() < ADJUSTMENT_COOLDOWN) {
                return;
            }

            ThreadMetrics metrics = collectThreadMetrics();

            OptimizationDecision decision = makeOptimizationDecision(metrics);

            if (decision.shouldAdjust()) {
                applyOptimization(decision);
                lastAdjustmentTime.set(currentTime);
            }
            
        } catch (Exception ignored) {
        }
    }

    private ThreadMetrics collectThreadMetrics() {
        ThreadMetrics metrics = new ThreadMetrics();
        
        metrics.threadCount = threadBean.getThreadCount();
        metrics.peakThreadCount = threadBean.getPeakThreadCount();
        metrics.totalStartedThreadCount = threadBean.getTotalStartedThreadCount();

        int queueSize = dynamicThreadPool.getQueue().size();
        int activeCount = dynamicThreadPool.getActiveCount();
        int poolSize = dynamicThreadPool.getPoolSize();
        
        metrics.queueUtilization = queueSize > 0 ? 
            (double) queueSize / (queueSize + dynamicThreadPool.getMaximumPoolSize()) : 0;
        metrics.activeRatio = poolSize > 0 ? (double) activeCount / poolSize : 0;

        long currentCompleted = completedTaskCount.get();
        long deltaTime = System.currentTimeMillis() - lastAdjustmentTime.get();
        metrics.throughput = deltaTime > 0 ? 
            (currentCompleted - metrics.lastCompletedCount) / (deltaTime / 1000.0) : 0;
        
        metrics.lastCompletedCount = currentCompleted;
        
        return metrics;
    }

    private void collectBukkitMetrics() {
        try {
            completedTaskCount.set(totalTaskCount.get());

            double tps = getServerTPS();
            if (tps < 15.0) {
                reduceThreadPoolSize();
            }
            
        } catch (Exception ignored) {
        }
    }
    private double getServerTPS() {
        try {
            Object minecraftServer = Bukkit.getServer().getClass()
                .getMethod("getServer").invoke(Bukkit.getServer());
            
            Object recentTps = minecraftServer.getClass()
                .getField("recentTps").get(minecraftServer);
            
            if (recentTps instanceof double[] tpsArray) {
                return tpsArray.length > 0 ? tpsArray[0] : 20.0;
            }
        } catch (Exception ignored) {
        }
        return 20.0;
    }

    private OptimizationDecision makeOptimizationDecision(ThreadMetrics metrics) {
        OptimizationDecision decision = new OptimizationDecision();

        if (metrics.queueUtilization > 0.8) {
            decision.action = OptimizationAction.INCREASE_THREADS;
            decision.reason = "队列积压严重 (" + String.format("%.1f%%", metrics.queueUtilization * 100) + ")";
        }
        else if (metrics.activeRatio < 0.3 && metrics.queueUtilization < 0.1) {
            decision.action = OptimizationAction.DECREASE_THREADS;
            decision.reason = "线程利用率低 (" + String.format("%.1f%%", metrics.activeRatio * 100) + ")";
        }
        else if (metrics.throughput > 0 && metrics.throughput < 10) {
            decision.action = OptimizationAction.INCREASE_THREADS;
            decision.reason = "吞吐量偏低 (" + String.format("%.1f", metrics.throughput) + " tasks/sec)";
        }
        else {
            decision.action = OptimizationAction.NO_CHANGE;
            decision.reason = "当前配置最优";
        }
        
        return decision;
    }
    private void applyOptimization(OptimizationDecision decision) {
        int currentCoreSize = dynamicThreadPool.getCorePoolSize();
        int newCoreSize = currentCoreSize;
        
        switch (decision.action) {
            case INCREASE_THREADS:
                newCoreSize = Math.min(currentCoreSize + 2, MAX_THREADS);
                break;
            case DECREASE_THREADS:
                newCoreSize = Math.max(currentCoreSize - 1, MIN_THREADS);
                break;
            case NO_CHANGE:
                return;
        }
        
        if (newCoreSize != currentCoreSize) {
            dynamicThreadPool.setCorePoolSize(newCoreSize);

        }
    }
    private void reduceThreadPoolSize() {
        int currentSize = dynamicThreadPool.getCorePoolSize();
        int newSize = Math.max(currentSize - 2, MIN_THREADS);
        
        if (newSize != currentSize) {
            dynamicThreadPool.setCorePoolSize(newSize);

        }
    }

    @SuppressWarnings("unused")
    public void submitTask(Runnable task) {
        totalTaskCount.incrementAndGet();
        dynamicThreadPool.submit(() -> {
            activeThreadCount.incrementAndGet();
            try {
                task.run();
            } finally {
                activeThreadCount.decrementAndGet();
            }
        });
    }

    public void shutdown() {
        
        if (monitoringService != null) {
            monitoringService.shutdown();
            try {
                if (!monitoringService.awaitTermination(5, TimeUnit.SECONDS)) {
                    monitoringService.shutdownNow();
                }
            } catch (InterruptedException e) {
                monitoringService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (dynamicThreadPool != null) {
            dynamicThreadPool.shutdown();
            try {
                if (!dynamicThreadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    dynamicThreadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                dynamicThreadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

    }
    
    private static class ThreadMetrics {
        int threadCount;
        int peakThreadCount;
        long totalStartedThreadCount;
        double queueUtilization;
        double activeRatio;
        double throughput;
        long lastCompletedCount;
    }
    
    private enum OptimizationAction {
        INCREASE_THREADS,
        DECREASE_THREADS,
        NO_CHANGE
    }
    
    private static class OptimizationDecision {
        OptimizationAction action = OptimizationAction.NO_CHANGE;
        String reason = "";
        
        public boolean shouldAdjust() {
            return action != OptimizationAction.NO_CHANGE;
        }
    }

    private static class SmartThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "SmartThread-" + threadNumber.getAndIncrement());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    private class SmartRejectedExecutionHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            try {
                r.run();
            } catch (Exception e) {
                plugin.getLogger().warning("任务执行失败: " + e.getMessage());
            }
        }
    }
}