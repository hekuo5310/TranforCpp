package com.github.tranforcpp.optimizer;

import com.github.tranforcpp.utils.FoliaCompatUtil;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 内存优化器
 * <p>
 * 负责监控和优化JVM内存使用，防止内存泄漏和过度消耗。
 * 通过定期清理缓存和对象池来维持良好的内存状态。
 * <p>
 * 主要功能：
 * - 内存使用监控
 * - 自动垃圾回收触发
 * - 对象池管理
 * - 缓存清理
 */
public class MemoryOptimizer {

    private final MemoryMXBean memoryBean;
    
    private final ConcurrentHashMap<String, Object> objectPool = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cacheExpiry = new ConcurrentHashMap<>();
    
    private FoliaCompatUtil.TaskHandle cleanupTask;
    private FoliaCompatUtil.TaskHandle monitorTask;
    
    private static final long CLEANUP_INTERVAL = 300000L;
    private static final long MONITOR_INTERVAL = 60000L;
    private static final double GC_TRIGGER_THRESHOLD = 0.85;
    private static final double FORCE_CLEANUP_THRESHOLD = 0.9;
    private static final double MODERATE_PRESSURE_THRESHOLD = 0.75;
    private static final int OBJECT_POOL_MAX_SIZE = 1000;
    private static final int MODERATE_POOL_SIZE = 500;
    
    public MemoryOptimizer() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
    }
    
    public void initialize() {
        startCleanupTask();
        startMonitorTask();
    }
    
    private void startCleanupTask() {
        cleanupTask = FoliaCompatUtil.runAsyncTaskTimer(this::performCleanup, 
            CLEANUP_INTERVAL, CLEANUP_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    private void startMonitorTask() {
        monitorTask = FoliaCompatUtil.runAsyncTaskTimer(this::monitorMemory, 
            MONITOR_INTERVAL, MONITOR_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    private void performCleanup() {
        cleanupExpiredCache();
        cleanObjectPool();
        triggerGarbageCollectionIfNeeded();
    }
    
    private void monitorMemory() {
        checkMemoryPressure();
    }
    
    private void cleanupExpiredCache() {
        long currentTime = System.currentTimeMillis();
        
        cacheExpiry.entrySet().removeIf(entry -> {
            if (currentTime > entry.getValue()) {
                objectPool.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    private void cleanObjectPool() {
        int poolSize = objectPool.size();
        if (poolSize > OBJECT_POOL_MAX_SIZE) {
            int removeCount = poolSize / 2;
            objectPool.entrySet().stream()
                .limit(removeCount)
                .map(Map.Entry::getKey)
                .forEach(objectPool::remove);
        }
    }
    
    private void triggerGarbageCollectionIfNeeded() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double usageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        
        if (usageRatio > GC_TRIGGER_THRESHOLD) {
            System.gc();
        }
    }
    
    private void checkMemoryPressure() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double usageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        
        if (usageRatio > FORCE_CLEANUP_THRESHOLD) {
            forceCleanup();
        } else if (usageRatio > MODERATE_PRESSURE_THRESHOLD) {
            cleanupExpiredCache();
            if (objectPool.size() > MODERATE_POOL_SIZE) {
                cleanObjectPool();
            }
        }
    }
    
    private void forceCleanup() {
        objectPool.clear();
        cacheExpiry.clear();
        System.gc();
    }
    
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        if (monitorTask != null) {
            monitorTask.cancel();
        }
        
        objectPool.clear();
        cacheExpiry.clear();
    }


}