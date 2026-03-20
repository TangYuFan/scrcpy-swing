package com.tyf.demo.gui;

import com.tyf.demo.service.ConstService;
import com.tyf.demo.service.ScrcpyService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;


/**
 *   @desc : 主窗体入口
 *   @auth : tyf
 *   @date : 2025-12-03 14:46:02
*/
public class MainFrame extends JFrame {

    private static MainFrame mainFrame;

    public MainFrame() {

        setTitle(ConstService.MAIN_TITLE);
        setMaximumSize(new Dimension(ConstService.MAIN_WIDTH, ConstService.MAIN_HEIGHT));
        setLocationRelativeTo(null);     // 居中
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setIconImage(((ImageIcon) ConstService.MAIN_ICON).getImage());  // 图标

        // 窗体入口
        this.add(new MainPanel());
        this.pack();
        this.setLocationRelativeTo(null);
        mainFrame = this;

        // 退出前结束 adb shell / 端口转发，避免 adb.exe 仍被占用导致下次启动无法覆盖
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                ScrcpyService.shutdown();
            }
        });
    }

    public static MainFrame getMainFrame() {
        return mainFrame;
    }
}
