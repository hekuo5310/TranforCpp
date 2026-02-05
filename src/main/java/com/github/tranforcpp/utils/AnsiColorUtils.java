package com.github.tranforcpp.utils;

/**
 * ANSI颜色代码工具类
 * <p>
 * 提供终端颜色代码的常量定义和实用方法。
 * 用于美化控制台输出和日志信息。
 * <p>
 * 支持的功能：
 * - 基础颜色代码
 * - 渐变文本生成
 * - 颜色字符串组合
 */
public class AnsiColorUtils {

    public static final String RESET = "\033[0m";
    public static final String BLACK = "\033[30m";
    public static final String RED = "\033[31m";
    public static final String GREEN = "\033[32m";
    public static final String YELLOW = "\033[33m";
    public static final String BLUE = "\033[34m";
    public static final String MAGENTA = "\033[35m";
    public static final String CYAN = "\033[36m";
    public static final String WHITE = "\033[37m";

    public static final String COLOR_45 = "\033[38;5;45m";
    public static final String COLOR_51 = "\033[38;5;51m";
    public static final String COLOR_87 = "\033[38;5;87m";
    public static final String COLOR_123 = "\033[38;5;123m";
    public static final String COLOR_159 = "\033[38;5;159m";
    public static final String COLOR_195 = "\033[38;5;195m";

    public static final String BG_BLACK = "\033[40m";
    public static final String BG_RED = "\033[41m";
    public static final String BG_GREEN = "\033[42m";
    public static final String BG_YELLOW = "\033[43m";
    public static final String BG_BLUE = "\033[44m";
    public static final String BG_MAGENTA = "\033[45m";
    public static final String BG_CYAN = "\033[46m";
    public static final String BG_WHITE = "\033[47m";

    public static final String BOLD = "\033[1m";
    public static final String UNDERLINE = "\033[4m";
    public static final String REVERSE = "\033[7m";

    public static final String[] LOGO_GRADIENT = {
        COLOR_45, COLOR_51, COLOR_87, COLOR_123, COLOR_159, COLOR_195
    };
    
    public static String colorize(String text, String color) {
        return color + text + RESET;
    }
    
    public static String[] createGradientText(String[] lines, String[] colors) {
        String[] result = new String[Math.min(lines.length, colors.length)];
        for (int i = 0; i < result.length; i++) {
            result[i] = colorize(lines[i], colors[i]);
        }
        return result;
    }
}