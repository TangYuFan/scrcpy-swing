package com.tyf.demo.service;

import com.tyf.demo.util.IconsTools;

import javax.swing.*;
import java.awt.*;


/**
 *   @desc : 常量类
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
*/
public final class ConstService {

    // ================================================================
    // 工作目录配置
    // ================================================================
    public static final String ROOT = "app_boot";                              // 工作父目录
    public static final String WORKSPACE = "C:\\"+ ROOT;                       // 工作目录
    public static final String LOG_DIR = "C:\\"+ ROOT +"\\log\\";              // 日志路径
    public static final String ADB_PATH = "C:\\"+ ROOT +"\\adb\\";             // adb 文件路径

    // ================================================================
    // Scrcpy 投屏配置
    // ================================================================
    /** scrcpy-server.jar 资源路径（位于 src/main/resources/scrcpy/） */
    public static final String SCRCPY_SERVER_RESOURCE = "/scrcpy/scrcpy-server-v3.3.4.jar";
    /** scrcpy-server.jar 文件名 */
    public static final String SCRCPY_SERVER_JAR_NAME = "scrcpy-server-v3.3.4.jar";
    /** 手机上存放 scrcpy-server.jar 的路径 */
    public static final String SCRCPY_DEVICE_SERVER_PATH = "/data/local/tmp/scrcpy-server.jar";
    /** Scrcpy 端口（视频和控制共用同一端口，通过多次连接区分） */
    public static final int SCRCPY_PORT = 27183;
    /** scrcpy-server 版本号 */
    public static final String SCRCPY_SERVER_VERSION = "3.3.4";
    /**
     * 设备编码最大边长（scrcpy {@code max_size}）。
     * - 0 = 原生分辨率（PC 软解压力最大）
     * - 1280 = 在多数设备上可明显改善流畅度
     * - 要清晰度可调大或设 0。
     */
    public static final int SCRCPY_MAX_SIZE = 1280;
    /** 最大帧率 */
    // 理论带宽和延迟
    //    本地 Socket (adb forward)	~100MB/s+	<1ms
    //    WiFi	~30-100Mbps	                    5-20ms
    //    USB	~480Mbps	                    <1ms

    public static final int SCRCPY_MAX_FPS = 120;
//    public static final int SCRCPY_MAX_FPS = 60;
    /** 为 true 时将解码后的帧绘制到 UI；为 false 时仅打抽样日志（排障用） */
    public static final boolean SCRCPY_DRAW_DECODED_TO_UI = true;


    public static final String MAIN_TITLE = "Mobile";  // 主窗体名称
    public static final Icon MAIN_ICON = IconsTools.app;    // 主窗体图标
    public static final int MAIN_WIDTH = 350;     // 主窗体宽
    public static final int MAIN_HEIGHT = 700;    // 主窗体高
    /** 是否启用窗口自动适应手机分辨率（去掉黑边） */
    public static final boolean AUTO_RESIZE_WINDOW = false;
    /** 窗口最大宽度（0 = 不限制） */
    public static final int MAX_WINDOW_WIDTH = 0;
    /** 窗口最大高度（0 = 不限制） */
    public static final int MAX_WINDOW_HEIGHT = 0;


    public static final String DEFAULT_FONT_NAME = "Microsoft YaHei UI";

    public static final Font FONT_NORMAL = new Font(DEFAULT_FONT_NAME, Font.PLAIN, 12);
    public static final Font FONT_BOLD = new Font(DEFAULT_FONT_NAME, Font.BOLD, 12);
    public static final Font FONT_TITLE = new Font(DEFAULT_FONT_NAME, Font.BOLD, 12);
    public static final Font FONT_SMALL = new Font(DEFAULT_FONT_NAME, Font.PLAIN, 11);

    // ================================================================
    // 现代主题配色 (Modern Theme Colors)
    // ================================================================
    /** 主色调 - 现代蓝 */
    public static final Color THEME_PRIMARY = new Color(30, 136, 229);
    /** 次要色 - 青色 */
    public static final Color THEME_SECONDARY = new Color(38, 166, 154);
    /** 主背景 - 浅灰白 */
    public static final Color THEME_BG = new Color(245, 245, 245);
    /** 表面色 - 纯白 */
    public static final Color THEME_SURFACE = new Color(255, 255, 255);
    /** 主文字 - 深灰 */
    public static final Color THEME_TEXT_PRIMARY = new Color(33, 33, 33);
    /** 次要文字 - 中灰 */
    public static final Color THEME_TEXT_SECONDARY = new Color(117, 117, 117);
    /** 边框色 - 浅灰 */
    public static final Color THEME_BORDER = new Color(224, 224, 224);
    /** 强调色 - 亮蓝 */
    public static final Color THEME_ACCENT = new Color(33, 150, 243);
    /** 悬停背景 - 淡蓝 */
    public static final Color THEME_HOVER = new Color(187, 222, 251);
    /** 按下背景 - 更深蓝 */
    public static final Color THEME_PRESSED = new Color(100, 181, 246);
    /** 成功色 - 绿色 */
    public static final Color THEME_SUCCESS = new Color(76, 175, 80);
    /** 警告色 - 橙色 */
    public static final Color THEME_WARNING = new Color(255, 152, 0);
    /** 错误色 - 红色 */
    public static final Color THEME_ERROR = new Color(244, 67, 54);
    /** 内容区背景 - 柔和灰 */
    public static final Color THEME_CONTENT_BG = new Color(238, 238, 238);

    // 兼容旧颜色常量（保留以避免其他地方报错）
    public static final Color COLOR_BG = THEME_BG;
    public static final Color COLOR_TITLE = THEME_TEXT_PRIMARY;
    public static final Color COLOR_BORDER = THEME_BORDER;
    public static final Color COLOR_ACCENT = THEME_PRIMARY;
    public static final Color COLOR_BLACK = THEME_TEXT_PRIMARY;
    public static final Color COLOR_LIGHT_GRAY = new Color(224, 224, 224);


    public static final int PADDING = 10;
    public static final int GAP = 8;
    public static final int LARGE_GAP = 15;



}
