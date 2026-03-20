package com.tyf.demo.service;


import com.tyf.demo.entity.Device;
import com.tyf.demo.gui.ContentPanel;
import com.tyf.demo.gui.MainFrame;
import com.tyf.demo.gui.MainPanel;
import com.tyf.demo.util.ExecutorsTools;
import com.tyf.demo.util.GuiTools;
import org.pmw.tinylog.Logger;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *   @desc : 管理设备连接
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
*/
public class ConnectService {

    private static final AtomicBoolean connecting = new AtomicBoolean(false);


    /**
     *   @desc : 打开设备并建立投屏连接
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static void connectDevice(Device device){

        if (!connecting.compareAndSet(false, true)) {
            return;
        }

        ExecutorsTools.connectThread.execute(()->{

            // 先关闭旧连接和 loading
            ScrcpyService.shutdown();
            ContentPanel.closeLoadingDialog();
            if (MainPanel.getContentPanel() != null) {
                MainPanel.getContentPanel().reset();
            }

            // 显示 loading 对话框
            JDialog loading = GuiTools.showLoading("opening", MainFrame.getMainFrame());

            // 将 loading 引用传递给 ContentPanel，第一帧渲染后会自动关闭
            ContentPanel.setLoadingDialog(loading);

            try {
                // 启动 scrcpy 投屏服务（异步启动解码线程）
                ScrcpyService.start(device);
            }
            catch (Exception e){
                e.printStackTrace();
                Logger.info("Scrcpy Start Fail："+e.getMessage()+"，"+e.getCause());
                // 启动失败，手动关闭 loading
                SwingUtilities.invokeLater(loading::dispose);
            }
            finally {
                connecting.set(false);
            }
        });

    }


}
