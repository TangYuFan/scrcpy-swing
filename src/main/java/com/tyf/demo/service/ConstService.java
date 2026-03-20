package com.tyf.demo.service;

import com.tyf.demo.util.IconsTools;

import javax.swing.*;
import java.awt.*;


/**
 *   @desc : 常量类
 *   @auth : tyf
 *   @date : 2025-12-03 14:43:31
*/
public final class ConstService {

    public static final String ROOT = "app_boot";  // 工作父目录
    public static final String WORKSPACE = "C:\\"+ ROOT;    // 工作目录
    public static final String LOG_DIR = "C:\\"+ ROOT +"\\log\\";      // 日志路径
    public static final String ADB_PATH = "C:\\"+ ROOT +"\\adb\\";    // adb 文件路径
    public static final int PC_CONNECT_TIMEOUT = 1000 * 5;          // PC 端连接超时时间 (legacy)

    public static final String MAIN_TITLE = "Mobile";  // 主窗体名称
    public static final Icon MAIN_ICON = IconsTools.app;    // 主窗体图标
    public static final int MAIN_WIDTH = 420;     // 主窗体宽
    public static final int MAIN_HEIGHT = 800;    // 主窗体高


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
