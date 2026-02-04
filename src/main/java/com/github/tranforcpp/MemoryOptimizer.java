package com.github.tranforcpp;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryOptimizer {
    
    private final TranforCPlusPlus plugin;
    private final MemoryMXBean memoryBean;
    
    private final ConcurrentHashMap<String, Object> objectPool = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cacheExpiry = new ConcurrentHashMap<>();
    
    private BukkitTask cleanupTask;
    private BukkitTask monitorTask;
    
    private static final long CLEANUP_INTERVAL = 300000L;
    private static final long MONITOR_INTERVAL = 60000L;
    
    public MemoryOptimizer(TranforCPlusPlus plugin) {
        this.plugin = plugin;
        this.memoryBean = ManagementFactory.getMemoryMXBean();
    }
    
    public void initialize() {
        startCleanupTask();
        startMonitorTask();
    }
    
    private void startCleanupTask() {
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::performCleanup, 
            CLEANUP_INTERVAL, CLEANUP_INTERVAL);
    }
    
    private void startMonitorTask() {
        monitorTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::monitorMemory, 
            MONITOR_INTERVAL, MONITOR_INTERVAL);
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
        if (objectPool.size() > 1000) {
            int removeCount = objectPool.size() / 2;
            objectPool.entrySet().stream()
                .limit(removeCount)
                .forEach(entry -> objectPool.remove(entry.getKey()));
        }
    }
    
    private void triggerGarbageCollectionIfNeeded() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double usageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        
        if (usageRatio > 0.85) {
            System.gc();
        }
    }
    
    private void checkMemoryPressure() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double usageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        
        if (usageRatio > 0.9) {
            forceCleanup();
        } else if (usageRatio > 0.75) {
            cleanupExpiredCache();
            if (objectPool.size() > 500) {
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