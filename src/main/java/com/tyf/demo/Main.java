package com.tyf.demo;

import com.tyf.demo.gui.InitPanel;
import com.tyf.demo.gui.MainFrame;
import org.pmw.tinylog.Logger;
import javax.swing.*;

/**
*   @desc : 上位机演示软件
*   @auth : tyf
*   @date : 2023-07-21  15:35:42
*/
public class Main {


    static {
        // 避免大图连续 drawImage 时 Windows D3D 管道与部分显卡驱动崩溃（第二帧闪退常见）
        System.setProperty("sun.java2d.d3d", "false");
        System.setProperty("sun.java2d.opengl", "false");
        System.setProperty("sun.java2d.noddraw", "true");
        com.formdev.flatlaf.FlatLightLaf.setup(); // 平光
    }

    /**
     *   @desc : 启动主窗体
     *   @auth : tyf
     *   @date : 2025-12-03 14:27:18
    */
    public static void initMainPanel(){

        Logger.info("init ..");

        // 启动
        SwingUtilities.invokeLater(() -> {
            // 主窗体启动
            MainFrame panel = new MainFrame();
            panel.setVisible(true);
            // 后台初始化任务开始
            InitPanel initDialog = new InitPanel(panel);
            initDialog.start();
        });

    }


    public static void main(String[] args){

        // 启动主窗体
        initMainPanel();

    }



}