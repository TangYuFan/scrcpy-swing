package com.tyf.demo.service;


import com.tyf.demo.entity.Device;
import com.tyf.demo.gui.MainFrame;
import com.tyf.demo.util.ExecutorsTools;
import com.tyf.demo.util.GuiTools;
import org.pmw.tinylog.Logger;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *   @desc : 管理设备连接
 *   @auth : tyf
 *   @date : 2026-03-19 09:54:15
*/
public class ConnectService {

    private static final AtomicBoolean connecting = new AtomicBoolean(false);


    // 打开设备
    public static void connectDevice(Device device){

        if (!connecting.compareAndSet(false, true)) {
            return;
        }

        ExecutorsTools.connectThread.execute(()->{

            // 提示框
            JDialog loading = GuiTools.showLoading("opening", MainFrame.getMainFrame());

            try {
                // scrcpy-server mode (video only for now)
                ScrcpyService.start(device);
            }
            catch (Exception e){
                e.printStackTrace();
                Logger.info("Scrcpy Start Fail："+e.getMessage()+"，"+e.getCause());
            }
            finally {
                connecting.set(false);
                loading.dispose(); // 关闭提示框
            }
        });

    }


}
