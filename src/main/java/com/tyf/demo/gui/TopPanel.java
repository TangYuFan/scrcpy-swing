package com.tyf.demo.gui;

import com.tyf.demo.entity.ButtonEditor;
import com.tyf.demo.entity.ButtonRenderer;
import com.tyf.demo.entity.Device;
import com.tyf.demo.entity.DeviceTableModel;
import com.tyf.demo.service.ConnectService;
import com.tyf.demo.service.ConstService;
import com.tyf.demo.service.ScrcpyService;
import com.tyf.demo.util.DeviceTools;
import com.tyf.demo.util.GuiTools;
import org.pmw.tinylog.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 *   @desc : 顶部工具栏
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
*/
public class TopPanel extends JPanel {

    // 切换手机弹出窗
    JDialog popup;
    // 手机端日志窗口
    JDialog mlogDialog;
    Process mlogProcess;

    public TopPanel() {

        setPreferredSize(new Dimension(0, 25));
        setLayout(new BorderLayout());

        // 左侧面板（从左往右）
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        leftPanel.setOpaque(false);

        // 右侧面板（从右往左）
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 2));
        rightPanel.setOpaque(false);

        JLabel swi = GuiTools.createLinkLabel("Switch", ConstService.FONT_NORMAL, ConstService.COLOR_BLACK);
        JLabel readme = GuiTools.createLinkLabel("Info", ConstService.FONT_NORMAL, ConstService.COLOR_BLACK);
        JLabel log = GuiTools.createLinkLabel("Log", ConstService.FONT_NORMAL, ConstService.COLOR_BLACK);
        JLabel mlog = GuiTools.createLinkLabel("MLog", ConstService.FONT_NORMAL, ConstService.COLOR_BLACK);


        // 打开日志路径
        log.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().open(new File(ConstService.LOG_DIR));
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        // 打开说明文档
        readme.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 快捷键说明文本
                String shortcuts =
                    "【快捷键说明】\n" +
                    "\n" +
                    "● 实体按钮（底部工具栏）：\n" +
                    "  Home      - 返回主屏幕\n" +
                    "  Back      - 返回键\n" +
                    "  Switch    - 任务切换\n" +
                    "  Power     - 电源键\n" +
                    "  Vol+/Vol- - 音量调节\n" +
                    "  Notify    - 展开通知面板\n" +
                    "\n" +
                    "● 鼠标操作：\n" +
                    "  左键点击    - 触摸点击\n" +
                    "  左键拖拽    - 触摸拖拽\n" +
                    "  右键点击    - 返回键（释放时触发）\n" +
                    "  滚轮滚动    - 页面滚动\n" +
                    "  Enter/Space - 点击屏幕中心\n" +
                    "\n" +
                    "● 键盘快捷键：\n" +
                    "  Ctrl+H          - Home键\n" +
                    "  Ctrl+B / Ctrl+\\ - 返回键\n" +
                    "  Ctrl+S          - 任务切换\n" +
                    "  Ctrl+M          - 菜单键\n" +
                    "  Ctrl+P          - 电源键\n" +
                    "  Ctrl+N          - 展开通知面板\n" +
                    "  Ctrl+Shift+N    - 折叠面板\n" +
                    "  Ctrl+Up         - 音量+\n" +
                    "  Ctrl+Down       - 音量-\n" +
                    "  ESC             - 返回键\n" +
                    "  Shift+F10       - 菜单键\n" +
                    "\n" +
                    "【高DPI缩放设置】\n" +
                    "1. 右键 exe → 属性 → " +
                            "   兼容性 → 更改高DPI设置\n" +
                    "2. 勾选「替代高DPI缩放行为」→ 应用程序\n";

                // 使用文本域显示，支持滚动
                JTextArea textArea = new JTextArea(shortcuts);
                textArea.setFont(new Font("微软雅黑", Font.PLAIN, 12));
                textArea.setEditable(false);
                textArea.setLineWrap(true);
                textArea.setWrapStyleWord(true);
                textArea.setBackground(new Color(250, 250, 250));

                JScrollPane scrollPane = new JScrollPane(textArea);
                scrollPane.setPreferredSize(new Dimension(360, 400));

                JOptionPane.showMessageDialog(
                        MainFrame.getMainFrame(),
                        scrollPane,
                        "Keyboard Shortcuts",
                        JOptionPane.PLAIN_MESSAGE
                );
            }
        });


        // 切换手机
        swi.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(popup!=null&&popup.isShowing()){
                    popup.dispose();
                    popup = null;
                    Logger.info("mouseClicked return");
                    return;
                }
                // 查询当前手机设备
                List<Device> devices = DeviceTools.listDevices();
                // 获取当前已连接的设备ID
                String connectedDeviceId = ScrcpyService.getActiveDeviceId();
                DeviceTableModel model = new DeviceTableModel(devices, connectedDeviceId);
                JTable table = new JTable(model);
                table.getColumn("Action").setCellRenderer(new ButtonRenderer());
                JScrollPane scrollPane = new JScrollPane(table);
                // 创建 popup
                JFrame owner = MainFrame.getMainFrame(); // 直接用主窗口
                popup = new JDialog(owner, "Devices");
                popup.setSize(300, 180);
                popup.setLayout(new BorderLayout());
                popup.add(scrollPane, BorderLayout.CENTER);
                // 手动计算位置（更稳定）
                Point p = owner.getLocationOnScreen();
                Dimension size = owner.getSize();
                popup.setLocation(
                        p.x + (size.width - popup.getWidth()) / 2,
                        p.y + (size.height - popup.getHeight()) / 2
                );
                // 监听 Window 移动
                ComponentListener listener = new ComponentAdapter() {
                    @Override
                    public void componentMoved(ComponentEvent e) {
                        Point p = owner.getLocationOnScreen();
                        Dimension size = owner.getSize();
                        popup.setLocation(
                                p.x + (size.width - popup.getWidth()) / 2,
                                p.y + (size.height - popup.getHeight()) / 2
                        );
                    }
                    @Override
                    public void componentResized(ComponentEvent e) {
                        componentMoved(e);
                    }
                };
                owner.addComponentListener(listener);
                // 点击事件
                ButtonEditor be = new ButtonEditor(new JCheckBox(), table, (device, isConnected) -> {
                    popup.dispose();
                    if (isConnected) {
                        // 已连接设备 → 断开连接
                        Logger.info("close：" + device.toString());
                        // 关闭 scrcpy 服务，清除所有连接资源
                        ScrcpyService.shutdown();
                        // 重置连接状态，允许重新连接
                        ConnectService.resetConnecting();
                        // 清除中间画面，防止最后一帧误导用户
                        ContentPanel contentPanel = MainPanel.getContentPanel();
                        if (contentPanel != null) {
                            contentPanel.reset();
                            contentPanel.repaint();
                        }
                    } else {
                        // 未连接设备 → 建立连接
                        Logger.info("open：" + device.toString());
                        ConnectService.connectDevice(device);
                    }
                });
                table.getColumn("Action").setCellEditor(be);
                // 设置列宽
                table.getColumnModel().getColumn(0).setPreferredWidth(100);
                table.getColumnModel().getColumn(1).setPreferredWidth(40);
                table.getColumnModel().getColumn(2).setPreferredWidth(80);
                // 关闭时移除监听
                popup.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        owner.removeComponentListener(listener);
                    }
                });
                popup.setVisible(true);
            }
        });

        // 打开手机端日志 (adb logcat | grep scrcpy)
        mlog.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 获取当前已连接的设备ID
                String deviceId = ScrcpyService.getActiveDeviceId();
                if (deviceId == null || deviceId.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(
                            MainFrame.getMainFrame(),
                            "Select Device First",
                            "Alert",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                    return;
                }

                // 如果已经打开，则关闭
                if (mlogDialog != null && mlogDialog.isShowing()) {
                    mlogDialog.dispose();
                    mlogDialog = null;
                    if (mlogProcess != null) {
                        mlogProcess.destroy();
                        mlogProcess = null;
                    }
                    return;
                }

                // 创建日志窗口
                mlogDialog = new JDialog(MainFrame.getMainFrame(), "Phone Log - " + deviceId);
                mlogDialog.setSize(800, 400);
                mlogDialog.setLayout(new BorderLayout());

                // 日志文本区域
                JTextArea logTextArea = new JTextArea();
                logTextArea.setEditable(false);
                logTextArea.setFont(new Font("Consolas", Font.PLAIN, 12));
                JScrollPane scrollPane = new JScrollPane(logTextArea);
                mlogDialog.add(scrollPane, BorderLayout.CENTER);

                // 底部按钮面板
                JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                JButton clearBtn = new JButton("clean");
                JButton closeBtn = new JButton("close");
                buttonPanel.add(clearBtn);
                buttonPanel.add(closeBtn);
                mlogDialog.add(buttonPanel, BorderLayout.SOUTH);

                // 清空按钮
                clearBtn.addActionListener(ev -> logTextArea.setText(""));

                // 关闭按钮
                closeBtn.addActionListener(ev -> {
                    if (mlogProcess != null) {
                        mlogProcess.destroy();
                        mlogProcess = null;
                    }
                    mlogDialog.dispose();
                    mlogDialog = null;
                });

                // 窗口关闭时也停止进程
                mlogDialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent ev) {
                        if (mlogProcess != null) {
                            mlogProcess.destroy();
                            mlogProcess = null;
                        }
                        mlogDialog = null;
                    }
                });

                // 启动 adb logcat 命令 (过滤 scrcpy tag)
                try {
                    String adbCmd = ConstService.ADB_PATH + "adb.exe";
                    // 使用 adb logcat 的 tag 过滤，只显示 scrcpy 标签
                    ProcessBuilder pb = new ProcessBuilder(
                            adbCmd, "-s", deviceId, "logcat", "-v", "time", "scrcpy:V", "*:S"
                    );
                    pb.redirectErrorStream(true);
                    mlogProcess = pb.start();

                    // 读取日志输出
                    new Thread(() -> {
                        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(mlogProcess.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                // 过滤空行和无关日志
                                if (line.trim().isEmpty()) continue;
                                final String finalLine = line;
                                SwingUtilities.invokeLater(() -> {
                                    logTextArea.append(finalLine + "\n");
                                    // 自动滚动到底部
                                    logTextArea.setCaretPosition(logTextArea.getText().length());
                                });
                            }
                        } catch (IOException ex) {
                            Logger.error("mlog read error: " + ex.getMessage());
                        }
                    }).start();

                    Logger.info("mlog started for device: " + deviceId);
                } catch (IOException ex) {
                    Logger.error("mlog start failed: " + ex.getMessage());
                    JOptionPane.showMessageDialog(
                            MainFrame.getMainFrame(),
                            "启动日志失败: " + ex.getMessage(),
                            "错误",
                            JOptionPane.ERROR_MESSAGE
                    );
                    mlogDialog.dispose();
                    mlogDialog = null;
                    return;
                }

                // 显示窗口
                mlogDialog.setLocationRelativeTo(MainFrame.getMainFrame());
                mlogDialog.setVisible(true);
            }
        });


        leftPanel.add(swi);
        leftPanel.add(readme);
        rightPanel.add(mlog);
        rightPanel.add(log);


        // 加入主面板
        add(leftPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.EAST);
    }
}