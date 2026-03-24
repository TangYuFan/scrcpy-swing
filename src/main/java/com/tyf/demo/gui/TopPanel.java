package com.tyf.demo.gui;

import com.tyf.demo.entity.ButtonEditor;
import com.tyf.demo.entity.ButtonRenderer;
import com.tyf.demo.entity.Device;
import com.tyf.demo.entity.DeviceTableModel;
import com.tyf.demo.service.ConnectService;
import com.tyf.demo.service.ConstService;
import com.tyf.demo.service.GameMappingConfig;
import com.tyf.demo.service.mapping.MappingEditUiFactory;
import com.tyf.demo.service.ScrcpyService;
import com.tyf.demo.util.DeviceTools;
import com.tyf.demo.util.GuiTools;
import com.tyf.demo.util.WifiTools;
import org.pmw.tinylog.Logger;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
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
    // Wifi 调试窗口
    JDialog wifiDialog;
    Process wifiProcess;

    public TopPanel() {

        setPreferredSize(new Dimension(0, 25));
        setLayout(new BorderLayout());

        // 左侧面板（从左往右）
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        leftPanel.setOpaque(false);

        // 右侧面板（从右往左）
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 2));
        rightPanel.setOpaque(false);

        JLabel swi = GuiTools.createLinkLabel("Switch", ConstService.FONT_NORMAL, ConstService.THEME_PRIMARY);
        JLabel wifi = GuiTools.createLinkLabel("Wifi", ConstService.FONT_NORMAL, ConstService.THEME_PRIMARY);
        JLabel readme = GuiTools.createLinkLabel("Info", ConstService.FONT_NORMAL, ConstService.THEME_PRIMARY);
        JLabel mapping = GuiTools.createLinkLabel("Map", ConstService.FONT_NORMAL, ConstService.THEME_PRIMARY);
        JLabel log = GuiTools.createLinkLabel("Log", ConstService.FONT_NORMAL, ConstService.THEME_PRIMARY);
        JLabel mlog = GuiTools.createLinkLabel("MLog", ConstService.FONT_NORMAL, ConstService.THEME_PRIMARY);


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


        // 游戏键位映射配置
        mapping.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showMappingDialog();
            }
        });


        // 打开说明文档
        readme.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 快捷键说明文本
                String shortcuts =
                        "----------------\n" +
                    "【快捷键说明】\n" +
                    "● 实体按钮（底部工具栏）：\n" +
                    "  Home      - 返回主屏幕\n" +
                    "  Back      - 返回键\n" +
                    "  Switch    - 任务切换\n" +
                    "  Power     - 电源键\n" +
                    "  Vol+/Vol- - 音量调节\n" +
                    "  Notify    - 展开通知面板\n" +
                    "● 鼠标操作：\n" +
                    "  左键点击    - 触摸点击\n" +
                    "  左键拖拽    - 触摸拖拽\n" +
                    "  右键点击    - 返回键（释放时触发）\n" +
                    "  滚轮滚动    - 页面滚动\n" +
                    "  Enter/Space - 点击屏幕中心\n" +
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
                    "----------------\n" +
                    "【Wifi调试】\n" +
                    "1.  点击 Wifi，将已经 USB 连接的设备开启无线调试\n" +
                    "2.  连接成功后可移除 USB，选择 Wifi 设备打开\n" +
                    "----------------\n" +
                    "【高DPI缩放设置】\n" +
                    "1. 右键 exe → 属性 → 兼容性 → 更改高DPI设置\n" +
                    "2. 勾选「替代高DPI缩放行为」→ 应用程序\n";

                // 使用文本域显示，支持滚动
                JTextArea textArea = new JTextArea(shortcuts);
                textArea.setFont(new Font("微软雅黑", Font.PLAIN, 12));
                textArea.setEditable(false);
                textArea.setLineWrap(true);
                textArea.setWrapStyleWord(true);
                textArea.setBackground(new Color(250, 250, 250));

                JScrollPane scrollPane = new JScrollPane(textArea);
                scrollPane.setPreferredSize(new Dimension(300, 400));

                JOptionPane.showMessageDialog(
                        MainFrame.getMainFrame(),
                        scrollPane,
                        "Keyboard Shortcuts",
                        JOptionPane.PLAIN_MESSAGE
                );
            }
        });


        // Wifi 无线调试
        wifi.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (wifiDialog != null && wifiDialog.isShowing()) {
                    wifiDialog.dispose();
                    wifiDialog = null;
                    if (wifiProcess != null) {
                        wifiProcess.destroy();
                        wifiProcess = null;
                    }
                    return;
                }

                // 创建日志窗口
                wifiDialog = new JDialog(MainFrame.getMainFrame(), "Wifi Debug");
                wifiDialog.setSize(300, 400);
                wifiDialog.setLayout(new BorderLayout());

                // 日志文本区域
                JTextArea logTextArea = new JTextArea();
                logTextArea.setEditable(false);
                logTextArea.setFont(new Font("Consolas", Font.PLAIN, 12));
                JScrollPane scrollPane = new JScrollPane(logTextArea);
                wifiDialog.add(scrollPane, BorderLayout.CENTER);

                // 底部按钮面板
                JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                JButton clearBtn = new JButton("clean");
                JButton closeBtn = new JButton("close");
                buttonPanel.add(clearBtn);
                buttonPanel.add(closeBtn);
                wifiDialog.add(buttonPanel, BorderLayout.SOUTH);

                // 清空按钮
                clearBtn.addActionListener(ev -> logTextArea.setText(""));

                // 关闭按钮
                closeBtn.addActionListener(ev -> {
                    if (wifiProcess != null) {
                        wifiProcess.destroy();
                        wifiProcess = null;
                    }
                    wifiDialog.dispose();
                    wifiDialog = null;
                });

                // 窗口关闭时也停止进程
                wifiDialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent ev) {
                        if (wifiProcess != null) {
                            wifiProcess.destroy();
                            wifiProcess = null;
                        }
                        wifiDialog = null;
                    }
                });

                // 显示窗口
                wifiDialog.setLocationRelativeTo(MainFrame.getMainFrame());
                wifiDialog.setVisible(true);

                // 执行 WifiTools 并实时输出日志
                new Thread(() -> {
                    try {
                        // 执行 WifiTools.autoConncetIPV4()
                        String adbCmd = ConstService.ADB_PATH + "adb.exe";
                        
                        // 先获取设备列表
                        ProcessBuilder devicesPb = new ProcessBuilder(adbCmd, "devices");
                        devicesPb.redirectErrorStream(true);
                        Process devicesProcess = devicesPb.start();
                        java.io.BufferedReader devicesReader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(devicesProcess.getInputStream(), Charset.forName("GBK")));
                        StringBuilder devicesOutput = new StringBuilder();
                        String line;
                        while ((line = devicesReader.readLine()) != null) {
                            devicesOutput.append(line).append("\n");
                        }
                        final String devicesStr = devicesOutput.toString();
                        SwingUtilities.invokeLater(() -> {
                            logTextArea.append("=== Devices ===\n");
                            logTextArea.append(devicesStr);
                            logTextArea.append("\n");
                        });

                        // 获取 USB 连接的设备
                        String[] lines = devicesStr.split("\n");
                        java.util.List<String> usbDevices = new java.util.ArrayList<>();
                        for (String dev : lines) {
                            if (!dev.contains("List of devices attached") && !dev.trim().isEmpty()) {
                                String devAddr = dev.replace("device", "").trim();
                                if (!devAddr.contains(":") || devAddr.contains("offline")) {
                                    usbDevices.add(devAddr);
                                }
                            }
                        }

                        if (usbDevices.isEmpty()) {
                            SwingUtilities.invokeLater(() -> {
                                logTextArea.append("No USB devices found!\n");
                            });
                            return;
                        }

                        for (String deviceName : usbDevices) {
                            // 获取设备 IP
                            ProcessBuilder ipPb = new ProcessBuilder(adbCmd, "-s", deviceName, "shell", "ip", "route");
                            ipPb.redirectErrorStream(true);
                            Process ipProcess = ipPb.start();
                            java.io.BufferedReader ipReader = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(ipProcess.getInputStream(), Charset.forName("GBK")));
                            StringBuilder ipOutput = new StringBuilder();
                            while ((line = ipReader.readLine()) != null) {
                                ipOutput.append(line).append("\n");
                            }
                            String ipOut = ipOutput.toString();
                            String ip = "";
                            if (ipOut.contains("src ")) {
                                ip = ipOut.substring(ipOut.indexOf("src ") + 4).trim().split(" ")[0];
                            }

                            final String finalIp = ip;

                            if (ip.isEmpty()) {
                                SwingUtilities.invokeLater(() -> {
                                    logTextArea.append("[" + deviceName + "] Failed to get IP\n");
                                });
                                continue;
                            }

                            SwingUtilities.invokeLater(() -> {
                                logTextArea.append("=== Processing " + deviceName + " (IP: " + finalIp + ") ===\n");
                            });

                            // 设置 TCPIP 模式
                            String port = WifiTools.randomPort();
                            ProcessBuilder tcpipPb = new ProcessBuilder(adbCmd, "-s", deviceName, "tcpip", port);
                            tcpipPb.redirectErrorStream(true);
                            Process tcpipProcess = tcpipPb.start();
                            java.io.BufferedReader tcpipReader = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(tcpipProcess.getInputStream(), Charset.forName("GBK")));
                            StringBuilder tcpipOutput = new StringBuilder();
                            while ((line = tcpipReader.readLine()) != null) {
                                tcpipOutput.append(line).append("\n");
                            }
                            final String tcpipStr = tcpipOutput.toString();
                            SwingUtilities.invokeLater(() -> {
                                logTextArea.append("[tcpip " + port + "] " + tcpipStr);
                            });

                            // 连接无线调试
                            String connectAddr = ip + ":" + port;
                            ProcessBuilder connectPb = new ProcessBuilder(adbCmd, "connect", connectAddr);
                            connectPb.redirectErrorStream(true);
                            Process connectProcess = connectPb.start();
                            java.io.BufferedReader connectReader = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(connectProcess.getInputStream(), Charset.forName("GBK")));
                            StringBuilder connectOutput = new StringBuilder();
                            while ((line = connectReader.readLine()) != null) {
                                connectOutput.append(line).append("\n");
                            }
                            final String connectStr = connectOutput.toString();
                            SwingUtilities.invokeLater(() -> {
                                logTextArea.append("[connect " + connectAddr + "] " + connectStr);
                                logTextArea.append("\n");
                            });
                        }

                        // 最终设备列表
                        SwingUtilities.invokeLater(() -> {
                            logTextArea.append("=== Final Devices ===\n");
                        });
                        ProcessBuilder finalPb = new ProcessBuilder(adbCmd, "devices");
                        finalPb.redirectErrorStream(true);
                        Process finalProcess = finalPb.start();
                        java.io.BufferedReader finalReader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(finalProcess.getInputStream(), Charset.forName("GBK")));
                        while ((line = finalReader.readLine()) != null) {
                            final String finalLine = line;
                            SwingUtilities.invokeLater(() -> {
                                logTextArea.append(finalLine + "\n");
                            });
                        }

                    } catch (Exception ex) {
                        Logger.error("wifi debug error: " + ex.getMessage());
                        SwingUtilities.invokeLater(() -> {
                            logTextArea.append("Error: " + ex.getMessage() + "\n");
                        });
                    }
                }).start();
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
        leftPanel.add(wifi);
        leftPanel.add(readme);
        leftPanel.add(mapping);
        rightPanel.add(mlog);
        rightPanel.add(log);


        // 加入主面板
        add(leftPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.EAST);
    }

    /**
     * @desc : 自定义键位列表：仅启用/禁用与逐项配置，不支持增删
     * @auth : tyf
     * @date : 2026-03-20
     */
    private void showMappingDialog() {
        GameMappingConfig.ensureBuiltinMappings();

        JDialog dialog = new JDialog(MainFrame.getMainFrame(), "自定义键位");
        dialog.setSize(650, 440);
        dialog.setLayout(new BorderLayout());

        String[] columnNames = {"名称", "配置", "触发", "启用", "配置"};
        java.util.List<GameMappingConfig.MappingEntry> mappingList = GameMappingConfig.getMappings();
        Object[][] data = new Object[mappingList.size()][5];

        for (int i = 0; i < mappingList.size(); i++) {
            GameMappingConfig.MappingEntry entry = mappingList.get(i);
            data[i][0] = entry.getName().isEmpty() ? "(未命名)" : entry.getName();
            data[i][1] = entry.getDisplayDesc();
            data[i][2] = entry.getTriggerDesc();
            data[i][3] = entry.isEnabled();
            data[i][4] = "配置";
        }

        JTable table = new JTable(data, columnNames) {
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 3) {
                    return Boolean.class;
                }
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 3;
            }
        };

        TableColumnModel cm = table.getColumnModel();
        // 名称（略窄）
        cm.getColumn(0).setPreferredWidth(56);
        cm.getColumn(0).setMinWidth(56);
        // 配置摘要
        cm.getColumn(1).setPreferredWidth(88);
        cm.getColumn(1).setMinWidth(88);
        // 触发（略窄）
        cm.getColumn(2).setPreferredWidth(54);
        cm.getColumn(2).setMinWidth(54);
        // 启用：仅勾选框
        cm.getColumn(3).setPreferredWidth(32);
        cm.getColumn(3).setMinWidth(32);
        cm.getColumn(3).setMaxWidth(44);
        // 配置按钮
        cm.getColumn(4).setPreferredWidth(54);
        cm.getColumn(4).setMinWidth(54);
        cm.getColumn(4).setMaxWidth(54);

        table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JCheckBox checkBox = new JCheckBox();
                checkBox.setSelected((Boolean) value);
                checkBox.setHorizontalAlignment(SwingConstants.CENTER);
                return checkBox;
            }
        });

        table.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(new JCheckBox()));

        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JButton button = new JButton("配置");
                button.setHorizontalAlignment(SwingConstants.CENTER);
                return button;
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row < 0 || col < 0) {
                    return;
                }
                if (col == 3) {
                    boolean newValue = !((Boolean) table.getValueAt(row, col));
                    table.setValueAt(newValue, row, col);
                    GameMappingConfig.MappingEntry entry = mappingList.get(row);
                    entry.setEnabled(newValue);
                    GameMappingConfig.saveMappings();
                    table.repaint();
                } else if (col == 4 && e.getButton() == MouseEvent.BUTTON1) {
                    GameMappingConfig.MappingEntry entry = mappingList.get(row);
                    MappingEditUiFactory.showEditDialog(dialog, entry, () -> {
                        dialog.dispose();
                        showMappingDialog();
                    });
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        dialog.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("关闭");
        closeBtn.addActionListener(e -> dialog.dispose());
        bottomPanel.add(closeBtn);
        dialog.add(bottomPanel, BorderLayout.SOUTH);

        dialog.setLocationRelativeTo(MainFrame.getMainFrame());
        dialog.setVisible(true);
        dialog.toFront();
    }
}