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
    /** 视频流转发端口（本地 PC 端口，adb forward 到手机的 localabstract:scrcpy） */
    public static final int SCRCPY_VIDEO_FORWARD_PORT = 27183;
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
    public static final int SCRCPY_MAX_FPS = 60;
    /** 为 true 时将解码后的帧绘制到 UI；为 false 时仅打抽样日志（排障用） */
    public static final boolean SCRCPY_DRAW_DECODED_TO_UI = true;


    public static final String MAIN_TITLE = "Mobile";  // 主窗体名称
    public static final Icon MAIN_ICON = IconsTools.app;    // 主窗体图标
    public static final int MAIN_WIDTH = 420;     // 主窗体宽
    public static final int MAIN_HEIGHT = 800;    // 主窗体高
    /** 是否启用窗口自动适应手机分辨率（去掉黑边） */
    public static final boolean AUTO_RESIZE_WINDOW = true;
    /** 窗口最大宽度（0 = 不限制） */
    public static final int MAX_WINDOW_WIDTH = 0;
    /** 窗口最大高度（0 = 不限制） */
    public static final int MAX_WINDOW_HEIGHT = 0;


    public static final String DEFAULT_FONT_NAME = "微软雅黑";

    public static final Font FONT_NORMAL = new Font(DEFAULT_FONT_NAME, Font.PLAIN, 13);
    public static final Font FONT_BOLD = new Font(DEFAULT_FONT_NAME, Font.BOLD, 13);
    public static final Font FONT_TITLE = new Font(DEFAULT_FONT_NAME, Font.BOLD, 13);
    public static final Font FONT_SMALL = new Font(DEFAULT_FONT_NAME, Font.PLAIN, 13);

    public static final Color COLOR_BG = new Color(250, 250, 250);  // 背景色
    public static final Color COLOR_TITLE = new Color(40, 40, 40);  // 标题文字颜色
    public static final Color COLOR_BORDER = new Color(220, 220, 220);  // 边框颜色
    public static final Color COLOR_ACCENT = new Color(30, 144, 255); // 蓝色主题点缀
    public static final Color COLOR_BLACK = Color.BLACK; // 黑
    public static final Color COLOR_LIGHT_GRAY = Color.lightGray; // 黑


    public static final int PADDING = 10;
    public static final int GAP = 8;
    public static final int LARGE_GAP = 15;



}
