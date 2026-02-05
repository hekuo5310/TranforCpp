package com.github.tranforcpp.compiler;

import com.github.tranforcpp.TranforCPlusPlus;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

/**
 * C++编译器
 * <p>
 * 负责自动检测和使用系统上的C++编译器来编译C++插件源代码。
 * 支持多种编译器（GCC、Clang、MSVC）和操作系统。
 * <p>
 * 主要特性：
 * - 自动编译器检测
 * - 编译结果缓存
 * - 跨平台支持
 * - 错误处理和诊断
 */
public class CppCompiler {

    private static final java.util.concurrent.ConcurrentHashMap<String, CachedCompileResult> compileCache = 
        new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile Boolean compilerChecked = null;
    private static final Object compilerCheckLock = new Object();
    private static volatile boolean errorAlreadyShown = false;
    private static final long CACHE_EXPIRY_TIME = 300000L;
    private static final long COMPILER_CHECK_TIMEOUT = 5;
    
    private static class CachedCompileResult {
        final File executable;
        final long timestamp;
        final int fileCount;
        
        CachedCompileResult(File exe, int count) {
            this.executable = exe;
            this.fileCount = count;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_TIME;
        }
    }
    
    public File compile(File cppDir) {
        List<File> cppFiles = findCppFiles(cppDir);
        if (cppFiles.isEmpty()) {
            return null;
        }

        String os = System.getProperty("os.name").toLowerCase();
        boolean isWindows = os.contains("win");
        
        File outputFile;
        String compiler = detectCompiler(isWindows);
        

        if (compiler == null && Boolean.FALSE.equals(compilerChecked)) {
            return null;
        }
        
        if (compiler == null) {
            if (!errorAlreadyShown) {
                TranforCPlusPlus.getInstance().getLogger().severe("未找到可用的C++编译器！请安装以下任一编译器：");
                TranforCPlusPlus.getInstance().getLogger().severe("- MinGW-w64 (推荐): https://www.mingw-w64.org/");
                TranforCPlusPlus.getInstance().getLogger().severe("- Visual Studio Build Tools");
                TranforCPlusPlus.getInstance().getLogger().severe("- 或将编译器路径添加到系统PATH环境变量");
                errorAlreadyShown = true;
            }
            compilerChecked = false;
            return null;
        }
        
        if (isWindows) {
            outputFile = new File(cppDir, "tranforcpp_plugin.exe");
        } else {
            outputFile = new File(cppDir, "tranforcpp_plugin");
        }

        List<String> command = buildCompileCommand(compiler, outputFile, cppFiles, isWindows);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                TranforCPlusPlus.getInstance().getLogger().severe("Compilation failed with exit code: " + exitCode);
                TranforCPlusPlus.getInstance().getLogger().severe("Compiler output:\n" + output);
                return null;
            }

            if (!outputFile.exists()) {
                TranforCPlusPlus.getInstance().getLogger().severe("Compilation succeeded but output file not found");
                return null;
            }

            return outputFile;

        } catch (IOException | InterruptedException e) {
            TranforCPlusPlus.getInstance().getLogger().severe("编译过程中发生错误: " + e.getMessage());
            if (e.getMessage().contains("CreateProcess error=2") || e.getMessage().contains("系统找不到指定的文件")) {
                TranforCPlusPlus.getInstance().getLogger().severe("解决方案：");
                TranforCPlusPlus.getInstance().getLogger().severe("1. 安装MinGW-w64: https://www.mingw-w64.org/downloads/");
                TranforCPlusPlus.getInstance().getLogger().severe("2. 安装MSYS2: https://www.msys2.org/");
                TranforCPlusPlus.getInstance().getLogger().severe("3. 或在插件配置文件中指定编译器完整路径");
                TranforCPlusPlus.getInstance().getLogger().severe("配置文件位置: plugins/TranforCPlusPlus/config.yml");
            }
            TranforCPlusPlus.getInstance().getLogger().log(Level.SEVERE, "编译过程发生错误", e);
            return null;
        }
    }

    private List<File> findCppFiles(File dir) {
        List<File> cppFiles = new ArrayList<>();
        File[] files = dir.listFiles();
        
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".cpp")) {
                    cppFiles.add(file);
                }
            }
        }
        return cppFiles;
    }
    
    private String detectCompiler(boolean isWindows) {
        if (isWindows) {
            String[] windowsCompilers = {
                "g++",
                "C:\\msys64\\mingw64\\bin\\g++.exe",
                "C:\\msys64\\ucrt64\\bin\\g++.exe",
                "C:\\msys64\\clang64\\bin\\g++.exe",
                "C:\\Program Files\\mingw-w64\\x86_64-8.1.0-posix-seh-rt_v6-rev0\\mingw64\\bin\\g++.exe",
                "C:\\TDM-GCC-64\\bin\\g++.exe",
                "cl.exe"
            };
            
            for (String compiler : windowsCompilers) {
                if (isCompilerAvailable(compiler)) {
                    TranforCPlusPlus.getInstance().getLogger().info("使用编译器: " + compiler);
                    return compiler;
                }
            }
            return null;
        } else {
            return isCompilerAvailable("g++") ? "g++" : null;
        }
    }
    
    private boolean isCompilerAvailable(String compilerPath) {
        try {
            ProcessBuilder pb = createCompilerCheckProcess(compilerPath);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            return process.waitFor(COMPILER_CHECK_TIMEOUT, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 构建编译命令
     */
    private List<String> buildCompileCommand(String compiler, File outputFile, List<File> cppFiles, boolean isWindows) {
        List<String> command = new ArrayList<>();
        command.add(compiler);
        command.add("-std=c++17");
        
        if (!isWindows) {
            command.add("-pthread");
        }
        
        command.add("-o");
        command.add(outputFile.getAbsolutePath());

        for (File cppFile : cppFiles) {
            command.add(cppFile.getAbsolutePath());
        }
        return command;
    }
    
    /**
     * 创建编译器检查进程
     */
    private ProcessBuilder createCompilerCheckProcess(String compilerPath) {
        String os = System.getProperty("os.name").toLowerCase();
        boolean isWindows = os.contains("win");
        
        if (isWindows) {
            if (compilerPath.equals("cl.exe")) {
                return new ProcessBuilder("cmd", "/c", "where", "cl");
            } else {
                return new ProcessBuilder("cmd", "/c", compilerPath, "--version");
            }
        } else {
            return new ProcessBuilder(compilerPath, "--version");
        }
    }
    
    /**
     * 读取进程输出
     */
    private String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }
    
    /**
     * 记录编译失败信息
     */
    private void logCompilationFailure(int exitCode, String compilerOutput) {
        TranforCPlusPlus.getInstance().getLogger().severe("Compilation failed with exit code: " + exitCode);
        TranforCPlusPlus.getInstance().getLogger().severe("Compiler output:\n" + compilerOutput);
    }
    
    public int countPlugins() {
        File cppDir = getCppDirectory();
        if (!cppDir.exists()) {
            return 0;
        }
        

        checkCompilerEarly();
        
        String cacheKey = cppDir.getAbsolutePath();
        CachedCompileResult cached = compileCache.get(cacheKey);
        
        if (cached != null && !cached.isExpired()) {
            return cached.fileCount;
        }
        
        List<File> files = findCppFiles(cppDir);
        int count = files.size();

        if (count > 0) {
            File dummyExecutable = new File(cppDir, "dummy.exe");
            compileCache.put(cacheKey, new CachedCompileResult(dummyExecutable, count));
            TranforCPlusPlus.getInstance().getLogger().fine("缓存更新: " + count + " 个插件文件");
        }
        
        return count;
    }
    
    private void checkCompilerEarly() {
        if (compilerChecked != null) {
            return;
        }
        
        synchronized (compilerCheckLock) {
            if (compilerChecked != null) {
                return;
            }
            
            String os = System.getProperty("os.name").toLowerCase();
            boolean isWindows = os.contains("win");
            
            String compiler = detectCompiler(isWindows);
            compilerChecked = (compiler != null);
            
            if (compiler == null && !errorAlreadyShown) {
                logMissingCompilerError();
                errorAlreadyShown = true;
            }
        }
    }
    
    /**
     * 记录缺失编译器错误信息
     */
    private void logMissingCompilerError() {
        TranforCPlusPlus.getInstance().getLogger().severe("未找到可用的C++编译器！请安装以下任一编译器：");
        TranforCPlusPlus.getInstance().getLogger().severe("- MinGW-w64 (推荐): https://www.mingw-w64.org/");
        TranforCPlusPlus.getInstance().getLogger().severe("- Visual Studio Build Tools");
        TranforCPlusPlus.getInstance().getLogger().severe("- 或将编译器路径添加到系统PATH环境变量");
    }
    public File getCppDirectory() {
        return new File(TranforCPlusPlus.getInstance().getDataFolder().getParentFile(), "C++ Plugins");
    }
    public List<File> getPluginFiles() {
        File cppDir = getCppDirectory();
        if (!cppDir.exists()) {
            return new ArrayList<>();
        }
        return findCppFiles(cppDir);
    }
}