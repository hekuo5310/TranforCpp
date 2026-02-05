package com.github.tranforcpp;

import com.github.tranforcpp.compiler.CppCompiler;
import com.github.tranforcpp.optimizer.SmartThreadOptimizer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 进程管理器
 * <p>
 * 负责管理C++插件进程的生命周期，包括启动、停止、消息传递等功能。
 * 实现了Bukkit事件监听器，用于捕获游戏事件并转发给C++插件。
 * <p>
 * 主要功能：
 * - 启动和管理C++进程
 * - 处理进程间通信
 * - 批量事件处理优化
 * - 智能资源管理
 */
public class ProcessManager implements Listener {

    private final TranforCPlusPlus plugin;
    private Process process;
    private BufferedReader inputReader;
    private BufferedWriter outputWriter;
    private final Gson gson = new Gson();
    private final BlockingQueue<JsonObject> messageQueue = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong messageCounter = new AtomicLong(0);
    private volatile Thread readerThread;
    private volatile ExecutorService senderExecutor;
    private final AtomicInteger activeSenders = new AtomicInteger(0);
    private final MiniMessage miniMessageInstance;

    private static final int MAX_QUEUE_SIZE = 2000;
    private static final int CORE_SENDER_THREADS = 2;
    private static final int FLUSH_THRESHOLD = 20;
    private static final int PROCESS_TERMINATION_TIMEOUT = 3;
    private static final int FORCE_TERMINATION_TIMEOUT = 1;
    private static final long RESTART_DELAY_MS = 100;

    public ProcessManager(TranforCPlusPlus plugin) {
        this.plugin = plugin;
        this.miniMessageInstance = MiniMessage.miniMessage();

    }

    public void start() {
        try {

            Bukkit.getPluginManager().registerEvents(this, plugin);
            
            File cppDir = new File(plugin.getDataFolder().getParentFile(), "C++ Plugins");
            if (!cppDir.exists()) {
                if (!cppDir.mkdirs()) {
                    plugin.getLogger().severe("Failed to create C++ Plugins directory: " + cppDir.getAbsolutePath());
                }
            }

            CppCompiler compiler = new CppCompiler();
            File executable = compiler.compile(cppDir);

            if (executable == null) {
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(executable.getAbsolutePath());
            pb.redirectErrorStream(true);
            process = pb.start();

            inputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            outputWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

            readerThread = new Thread(this::readMessages, "TranforC++-Reader");
            readerThread.setDaemon(true);
            readerThread.setPriority(Thread.NORM_PRIORITY);
            readerThread.start();

            initializeSenderExecutor();

            running.set(true);
            plugin.getLogger().info("C++ plugin process started with performance optimizations");

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start C++ process: " + e.getMessage());
            plugin.getLogger().severe("Exception: " + e);
        }
    }

    private void readMessages() {
        try {
            String line;
            while (running.get() && (line = inputReader.readLine()) != null) {
                try {
                    JsonObject json = gson.fromJson(line, JsonObject.class);
                    if (json != null) {
                        handleCppMessage(json);
                        messageCounter.incrementAndGet();
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to parse C++ message: " + line);
                    // 继续处理后续消息
                }
            }
        } catch (IOException e) {
            if (running.get() && process != null && process.isAlive()) {
                plugin.getLogger().severe("Error reading from C++ process: " + e.getMessage());
            }
            // 正常的连接断开不需要记录错误
        }
    }
    
    private void handleExecuteCommand(JsonObject json) {
        try {
            String command = json.get("command").getAsString();
            if (command != null && !command.trim().isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("执行命令失败: " + e.getMessage());
        }
    }

    private ExecutorService createSmartThreadPoolAdapter() {
        return new AbstractExecutorService() {
            private final ExecutorService delegate = Executors.newFixedThreadPool(
                CORE_SENDER_THREADS,
                r -> {
                    Thread t = new Thread(r, "TranforC++-SmartSender-" + activeSenders.incrementAndGet());
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                }
            );
            
            @Override
            public void execute(Runnable command) {
                SmartThreadOptimizer optimizer = plugin.getThreadOptimizer();
                if (optimizer != null) {
                    optimizer.submitTask(command);
                } else {
                    delegate.execute(command);
                }
            }
            
            // 委托所有ExecutorService方法到实际的线程池
            @Override public void shutdown() { delegate.shutdown(); }
            @Override public List<Runnable> shutdownNow() { return delegate.shutdownNow(); }
            @Override public boolean isShutdown() { return delegate.isShutdown(); }
            @Override public boolean isTerminated() { return delegate.isTerminated(); }
            @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException { 
                return delegate.awaitTermination(timeout, unit); 
            }
            @Override public <T> Future<T> submit(Callable<T> task) { return delegate.submit(task); }
            @Override public <T> Future<T> submit(Runnable task, T result) { return delegate.submit(task, result); }
            @Override public Future<?> submit(Runnable task) { return delegate.submit(task); }
            @Override public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException { 
                return delegate.invokeAll(tasks); 
            }
            @Override public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException { 
                return delegate.invokeAll(tasks, timeout, unit); 
            }
            @Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException { 
                return delegate.invokeAny(tasks); 
            }
            @Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException { 
                return delegate.invokeAny(tasks, timeout, unit); 
            }
        };
    }

    private void initializeSenderExecutor() {

        senderExecutor = createSmartThreadPoolAdapter();
        
        // 初始化消息发送工作者线程
        for (int i = 0; i < CORE_SENDER_THREADS; i++) {
            senderExecutor.submit(this::sendMessagesWorker);
        }
    }
    
    private void sendMessagesWorker() {
        try {
            while (running.get() && process != null && process.isAlive()) {
                JsonObject msg = messageQueue.poll(50, TimeUnit.MILLISECONDS);
                if (msg != null) {
                    outputWriter.write(gson.toJson(msg));
                    outputWriter.newLine();
                    // 当队列较小时立即刷新以减少延迟
                    if (messageQueue.size() < FLUSH_THRESHOLD) {
                        outputWriter.flush();
                    }
                    messageCounter.incrementAndGet();
                }
            }
        } catch (InterruptedException e) {
            // 线程被中断是正常的关闭过程
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            if (running.get()) {
                plugin.getLogger().warning("Error in message sender worker: " + e.getMessage());
            }
        }
    }

    private void handleCppMessage(JsonObject json) {
        try {
            String action = json.get("action").getAsString();
            
            switch (action) {
                case "broadcast":
                    handleBroadcast(json);
                    break;
                case "sendMessage":
                    handlePrivateMessage(json);
                    break;
                case "console":
                    plugin.getLogger().info(json.get("message").getAsString());
                    break;
                case "executeCommand":
                    handleExecuteCommand(json);
                    break;
                default:
                    plugin.getLogger().warning("Unknown action: " + action);
                    break;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error handling C++ message: " + e.getMessage());
        }
    }
    
    private void handleBroadcast(JsonObject json) {
        try {
            Component broadcastMessage = miniMessageInstance.deserialize(json.get("message").getAsString());
            Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
            for (Player player : players) {
                player.sendMessage(broadcastMessage);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error broadcasting message: " + e.getMessage());
        }
    }
    
    private void handlePrivateMessage(JsonObject json) {
        try {
            String playerName = json.get("player").getAsString();
            Player player = Bukkit.getPlayerExact(playerName);
            if (player != null && player.isOnline()) {
                Component privateMessage = miniMessageInstance.deserialize(json.get("message").getAsString());
                player.sendMessage(privateMessage);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error sending private message: " + e.getMessage());
        }
    }

    private final Queue<JsonObject> batchBuffer = new LinkedList<>();
    private static final int BATCH_SIZE = 30;
    private static final long BATCH_TIMEOUT_MS = 50;
    private static final int MAX_BATCH_PROCESSING_TIME = 5;
    private static final double QUEUE_SPACE_THRESHOLD = 0.8;
    private volatile long lastBatchTime = System.currentTimeMillis();
    
    public void sendEvent(String eventName, Object... args) {
        if (!running.get()) {
            return;
        }

        if (messageQueue.size() > MAX_QUEUE_SIZE) {
            plugin.getLogger().warning("消息队列已满，丢弃事件: " + eventName);
            return;
        }
        
        try {
            JsonObject json = new JsonObject();
            json.addProperty("event", eventName);
            
            com.google.gson.JsonArray argsArray = new com.google.gson.JsonArray();
            for (Object arg : args) {
                argsArray.add(arg != null ? arg.toString() : "null");
            }
            json.add("args", argsArray);
            
            synchronized (batchBuffer) {
                batchBuffer.offer(json);
                // 批处理触发条件：达到批次大小或超时
                if (batchBuffer.size() >= BATCH_SIZE || 
                    System.currentTimeMillis() - lastBatchTime > BATCH_TIMEOUT_MS) {
                    flushBatch();
                }
            }

            dispatchToOtherPluginsSync(eventName, args);
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error sending event " + eventName + ": " + e.getMessage());
        }
    }
    
    private void flushBatch() {
        if (batchBuffer.isEmpty()) return;
        
        long startTime = System.nanoTime();
        List<JsonObject> batch = new ArrayList<>(Math.min(batchBuffer.size(), BATCH_SIZE));
        JsonObject msg;
        int processed = 0;
        
        // 构建批处理消息
        while (processed < BATCH_SIZE && (msg = batchBuffer.poll()) != null) {
            batch.add(msg);
            processed++;
            
            // 防止批处理时间过长影响性能
            if ((System.nanoTime() - startTime) / 1_000_000 > MAX_BATCH_PROCESSING_TIME) {
                break;
            }
        }
        
        if (!batch.isEmpty()) {
            // 检查队列空间，避免阻塞
            boolean queueHasSpace = messageQueue.size() < (MAX_QUEUE_SIZE * QUEUE_SPACE_THRESHOLD);
            if (queueHasSpace) {
                messageQueue.addAll(batch);
                synchronized (messageQueue) {
                    messageQueue.notify();
                }
            } else {
                plugin.getLogger().warning("消息队列接近容量上限，跳过本次批处理");
            }
        }
        
        lastBatchTime = System.currentTimeMillis();
    }
    
    private void dispatchToOtherPluginsSync(String eventName, Object... args) {
        try {
            org.bukkit.event.Event customEvent = createCustomEvent(eventName, args);
            Bukkit.getPluginManager().callEvent(customEvent);
        } catch (Exception e) {
            plugin.getLogger().warning("事件分发失败: " + e.getMessage());
        }
    }
    
    private org.bukkit.event.Event createCustomEvent(String eventName, Object... args) {
        return new GenericTranforCEvent(eventName, args);
    }
    
    public static class GenericTranforCEvent extends org.bukkit.event.Event {
        private static final org.bukkit.event.HandlerList handlers = new org.bukkit.event.HandlerList();
        private final String eventName;
        private final Object[] args;
        public GenericTranforCEvent(String eventName, Object... args) {
            super(false);
            this.eventName = eventName;
            this.args = args;
        }
        
        public String getEventName() { return eventName; }

        public Object getArg(int index) {
            return index >= 0 && index < args.length ? args[index] : null;
        }

        public int getArgCount() { return args.length; }

        @Override
        public org.bukkit.event.HandlerList getHandlers() { return handlers; }


    }
    
    public void stop() {
        running.set(false);

        messageQueue.clear();
        
        if (outputWriter != null) {
            try {
                outputWriter.write("{\"event\":\"shutdown\"}\n");
                outputWriter.flush();
            } catch (IOException e) {
                plugin.getLogger().warning("Error sending shutdown message: " + e.getMessage());
            } finally {
                try {
                    outputWriter.close();
                } catch (IOException ignored) {}
            }
        }

        if (process != null) {
            gracefullyTerminateProcess();
        }

        if (readerThread != null && readerThread.isAlive()) {
            readerThread.interrupt();
        }
        if (senderExecutor != null && !senderExecutor.isShutdown()) {
            shutdownExecutorService(senderExecutor);
        }

        // 清理资源引用
        readerThread = null;
        senderExecutor = null;
        inputReader = null;
        outputWriter = null;
        process = null;
        
        plugin.getLogger().info("ProcessManager stopped. Messages processed: " + messageCounter.get());
        messageCounter.set(0);
    }

    public void restart() {
        stop();
        try {
            Thread.sleep(RESTART_DELAY_MS);
        } catch (InterruptedException e) {
            plugin.getLogger().warning("Interrupted during restart delay: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
        start();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        sendEvent("PlayerJoin", event.getPlayer().getName());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sendEvent("PlayerQuit", event.getPlayer().getName());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        sendEvent("BlockBreak", event.getPlayer().getName(), event.getBlock().getType().name());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        sendEvent("BlockPlace", event.getPlayer().getName(), event.getBlock().getType().name());
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        sendEvent("EntityDamage", event.getEntity().getName(), String.valueOf(event.getDamage()));
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        sendEvent("EntityDeath", event.getEntity().getName());
    }

    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        String deathMsg = "Player died";
        if (event.deathMessage() != null) {
            deathMsg = String.valueOf(event.deathMessage());
        }
        sendEvent("PlayerDeath", event.getEntity().getName(), deathMsg);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        sendEvent("InventoryClick", event.getWhoClicked().getName(), String.valueOf(event.getSlot()), event.getCurrentItem() != null ? event.getCurrentItem().getType().name() : "AIR");
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        sendEvent("InventoryOpen", event.getPlayer().getName());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        sendEvent("InventoryClose", event.getPlayer().getName());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        sendEvent("PlayerMove", event.getPlayer().getName());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        sendEvent("PlayerRespawn", event.getPlayer().getName());
    }

    @EventHandler
    public void onBlockIgnite(BlockIgniteEvent event) {
        String playerName = event.getPlayer() != null ? event.getPlayer().getName() : "null";
        sendEvent("BlockIgnite", playerName, event.getBlock().getType().name());
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        sendEvent("EntitySpawn", event.getEntityType().name());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        String itemName = event.getItem() != null ? event.getItem().getType().name() : "null";
        sendEvent("PlayerInteract", event.getPlayer().getName(), event.getAction().name(), itemName);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        sendEvent("PlayerDropItem", event.getPlayer().getName(), event.getItemDrop().getItemStack().getType().name());
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            sendEvent("PlayerPickupItem", player.getName(), event.getItem().getItemStack().getType().name());
        }
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        sendEvent("ServerCommand", event.getSender().getName(), event.getCommand());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        sendEvent("WorldLoad", event.getWorld().getName());
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        sendEvent("WeatherChange", event.getWorld().getName(), String.valueOf(event.toWeatherState()));
    }

    @EventHandler
    public void onHangingBreak(HangingBreakEvent event) {
        sendEvent("HangingBreak", event.getEntity().getType().name(), event.getCause().name());
    }
    
    /**
     * 优雅地终止C++进程
     */
    private void gracefullyTerminateProcess() {
        try {
            process.destroy();
            if (!process.waitFor(PROCESS_TERMINATION_TIMEOUT, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(FORCE_TERMINATION_TIMEOUT, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            plugin.getLogger().warning("Interrupted while waiting for process to terminate: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 安全关闭ExecutorService
     */
    private void shutdownExecutorService(ExecutorService executor) {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                        plugin.getLogger().warning("senderExecutor" + " failed to terminate properly");
                    }
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                plugin.getLogger().warning("senderExecutor" + " shutdown interrupted: " + e.getMessage());
            }
        }
    }
}