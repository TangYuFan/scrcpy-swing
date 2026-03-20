package com.tyf.demo;

import com.tyf.demo.gui.InitPanel;
import com.tyf.demo.gui.MainFrame;
import org.pmw.tinylog.Logger;
import javax.swing.*;

/**
 *   @desc : 应用入口
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
*/
public class Main {


    static {
        com.formdev.flatlaf.FlatLightLaf.setup(); // 平光
    }

    /**
     *   @desc : 启动主窗体
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
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
        initMainPanel();
    }



}