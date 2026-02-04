package com.github.tranforcpp.compiler;

import com.github.tranforcpp.TranforCPlusPlus;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class CppCompiler {

    private static final java.util.concurrent.ConcurrentHashMap<String, CachedCompileResult> compileCache = 
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_TIME = 300000L; // 5分钟
    
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
        
        if (compiler == null) {
            TranforCPlusPlus.getInstance().getLogger().severe("未找到可用的C++编译器！请安装以下任一编译器：");
            TranforCPlusPlus.getInstance().getLogger().severe("- MinGW-w64 (推荐): https://www.mingw-w64.org/");
            TranforCPlusPlus.getInstance().getLogger().severe("- Visual Studio Build Tools");
            TranforCPlusPlus.getInstance().getLogger().severe("- 或将编译器路径添加到系统PATH环境变量");
            return null;
        }
        
        if (isWindows) {
            outputFile = new File(cppDir, "tranforcpp_plugin.exe");
        } else {
            outputFile = new File(cppDir, "tranforcpp_plugin");
        }

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
            ProcessBuilder pb;
            String os = System.getProperty("os.name").toLowerCase();
            boolean isWindows = os.contains("win");
            
            if (isWindows) {
                if (compilerPath.equals("cl.exe")) {
                    pb = new ProcessBuilder("cmd", "/c", "where", "cl");
                } else {
                    pb = new ProcessBuilder("cmd", "/c", compilerPath, "--version");
                }
            } else {
                pb = new ProcessBuilder(compilerPath, "--version");
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    public int countPlugins() {
        File cppDir = getCppDirectory();
        if (!cppDir.exists()) {
            return 0;
        }
        
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
    
    public File getCppDirectory() {
        return new File(TranforCPlusPlus.getInstance().getDataFolder().getParentFile(), "C+plugins");
    }
    
    public List<File> getPluginFiles() {
        File cppDir = getCppDirectory();
        if (!cppDir.exists()) {
            return new ArrayList<>();
        }
        return findCppFiles(cppDir);
    }
    

    

}