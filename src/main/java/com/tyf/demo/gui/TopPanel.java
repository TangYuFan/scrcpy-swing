package com.tyf.demo.gui;

import com.tyf.demo.entity.ButtonEditor;
import com.tyf.demo.entity.ButtonRenderer;
import com.tyf.demo.entity.Device;
import com.tyf.demo.entity.DeviceTableModel;
import com.tyf.demo.service.ConnectService;
import com.tyf.demo.service.ConstService;
import com.tyf.demo.service.GameMappingConfig;
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

    private void showMappingDialog() {
        JDialog dialog = new JDialog(MainFrame.getMainFrame(), "键位映射");
        dialog.setSize(800, 450);
        dialog.setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel modeLabel = new JLabel("模式:");
        JRadioButton normalModeBtn = new JRadioButton("正常");
        JRadioButton mappingModeBtn = new JRadioButton("游戏映射");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(normalModeBtn);
        modeGroup.add(mappingModeBtn);

        if (GameMappingConfig.isMappingMode()) {
            mappingModeBtn.setSelected(true);
        } else {
            normalModeBtn.setSelected(true);
        }

        normalModeBtn.addActionListener(e -> GameMappingConfig.setMappingMode(false));
        mappingModeBtn.addActionListener(e -> GameMappingConfig.setMappingMode(true));

        JButton addBtn = new JButton("+ 新增");

        topPanel.add(modeLabel);
        topPanel.add(normalModeBtn);
        topPanel.add(mappingModeBtn);
        topPanel.add(Box.createHorizontalStrut(20));
        topPanel.add(addBtn);

        String[] columnNames = {"名称", "类型", "配置", "触发", "启用", "操作"};
        java.util.List<GameMappingConfig.MappingEntry> mappingList = GameMappingConfig.getMappings();
        Object[][] data = new Object[mappingList.size()][6];

        for (int i = 0; i < mappingList.size(); i++) {
            GameMappingConfig.MappingEntry entry = mappingList.get(i);
            data[i][0] = entry.getName().isEmpty() ? "(未命名)" : entry.getName();
            data[i][1] = entry.getType().getDesc();
            data[i][2] = entry.getDisplayDesc();
            data[i][3] = entry.getTriggerDesc();
            data[i][4] = entry.isEnabled();
            data[i][5] = "操作";
        }

        JTable table = new JTable(data, columnNames) {
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 4) return Boolean.class;
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 4 || column == 5;
            }
        };

        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(60);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(60);
        table.getColumnModel().getColumn(4).setPreferredWidth(40);
        table.getColumnModel().getColumn(5).setPreferredWidth(60);

        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JCheckBox checkBox = new JCheckBox();
                checkBox.setSelected((Boolean) value);
                checkBox.setHorizontalAlignment(SwingConstants.CENTER);
                return checkBox;
            }
        });

        table.getColumnModel().getColumn(4).setCellEditor(new DefaultCellEditor(new JCheckBox()));

        table.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JButton button = new JButton("操作");
                button.setHorizontalAlignment(SwingConstants.CENTER);
                return button;
            }
        });

        final JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem editItem = new JMenuItem("编辑");
        JMenuItem deleteItem = new JMenuItem("删除");
        popupMenu.add(editItem);
        popupMenu.add(deleteItem);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (col == 4) {
                    table.setValueAt(!((Boolean) table.getValueAt(row, col)), row, col);
                    GameMappingConfig.MappingEntry entry = mappingList.get(row);
                    entry.setEnabled((Boolean) table.getValueAt(row, col));
                } else if (col == 5) {
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        popupMenu.show(table, e.getX(), e.getY());
                    }
                }
            }
        });

        editItem.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                showEditMappingDialog(row, mappingList.get(row));
            }
        });

        deleteItem.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                int confirm = JOptionPane.showConfirmDialog(dialog, "确定删除此映射？", "确认删除", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    GameMappingConfig.MappingEntry entry = mappingList.get(row);
                    GameMappingConfig.removeMapping(entry.getId());
                    GameMappingConfig.saveMappings();
                    GameMappingConfig.loadMappings();
                    dialog.dispose();
                    showMappingDialog();
                }
            }
        });

        addBtn.addActionListener(e -> {
            GameMappingConfig.MappingEntry newEntry = new GameMappingConfig.MappingEntry();
            GameMappingConfig.addMapping(newEntry);
            GameMappingConfig.saveMappings();
            GameMappingConfig.loadMappings();
            dialog.dispose();
            showMappingDialog();
        });

        JScrollPane scrollPane = new JScrollPane(table);
        dialog.add(topPanel, BorderLayout.NORTH);
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

    private void showEditMappingDialog(int rowIndex, GameMappingConfig.MappingEntry entry) {
        JDialog editDialog = new JDialog(MainFrame.getMainFrame(), "编辑映射");
        editDialog.setModal(true);

        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JTextField nameField = new JTextField(entry.getName(), 15);

        JComboBox<GameMappingConfig.MappingType> typeCombo = new JComboBox<>(GameMappingConfig.MappingType.values());
        typeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof GameMappingConfig.MappingType) {
                    setText(((GameMappingConfig.MappingType) value).getDesc());
                }
                return this;
            }
        });
        typeCombo.setSelectedItem(entry.getType());

        JComboBox<GameMappingConfig.TriggerType> triggerCombo = new JComboBox<>(GameMappingConfig.TriggerType.values());
        triggerCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof GameMappingConfig.TriggerType) {
                    setText(((GameMappingConfig.TriggerType) value).getDesc());
                }
                return this;
            }
        });
        triggerCombo.setSelectedItem(entry.getTriggerType());

        JTextField keyField = new JTextField(entry.getKeyName() != null ? entry.getKeyName() : "", 10);
        JTextField xField = new JTextField(String.valueOf(entry.getPhoneX()), 6);
        JTextField yField = new JTextField(String.valueOf(entry.getPhoneY()), 6);
        JTextField sensitivityField = new JTextField(String.valueOf(entry.getMouseSensitivity()), 5);

        Runnable updateForm = () -> {
            formPanel.removeAll();

            GameMappingConfig.MappingType type = (GameMappingConfig.MappingType) typeCombo.getSelectedItem();

            JLabel typeHint = new JLabel("<html><span style='color:#333;font-size:12px;font-weight:bold;'>" + type.getDesc() + "</span> <span style='color:gray;font-size:11px;'>" + type.getHelp() + "</span></html>");
            typeHint.setBorder(BorderFactory.createEmptyBorder(2, 5, 8, 5));
            formPanel.add(typeHint);

            formPanel.add(createRow("名称:", nameField));

            JPanel typeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
            typeRow.add(new JLabel("类型:"));
            typeRow.add(typeCombo);
            formPanel.add(typeRow);

            if (type == GameMappingConfig.MappingType.MOUSE_MOVE) {
                formPanel.add(createRow("灵敏度:", sensitivityField));
            } else if (type == GameMappingConfig.MappingType.DRAG) {
            } else if (type == GameMappingConfig.MappingType.SWIPE) {
            } else {
                JPanel triggerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
                triggerRow.add(new JLabel("触发:"));
                triggerRow.add(triggerCombo);
                formPanel.add(triggerRow);
                formPanel.add(createRow("键位:", keyField));

                if (type == GameMappingConfig.MappingType.CLICK) {
                    JPanel xyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
                    xyRow.add(new JLabel("坐标:"));
                    xyRow.add(new JLabel("X"));
                    xyRow.add(xField);
                    xyRow.add(new JLabel("Y"));
                    xyRow.add(yField);
                    formPanel.add(xyRow);
                }
            }

            formPanel.revalidate();
            formPanel.repaint();
            editDialog.pack();
            
            int maxWidth = 400;
            int maxHeight = 450;
            if (editDialog.getWidth() > maxWidth) {
                editDialog.setSize(maxWidth, editDialog.getHeight());
            }
            if (editDialog.getHeight() > maxHeight) {
                editDialog.setSize(editDialog.getWidth(), maxHeight);
            }
            
            editDialog.setLocationRelativeTo(MainFrame.getMainFrame());
        };

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        JButton saveBtn = new JButton("保存");
        JButton cancelBtn = new JButton("取消");
        buttonPanel.add(saveBtn);
        buttonPanel.add(cancelBtn);

        editDialog.add(formPanel, BorderLayout.CENTER);
        editDialog.add(buttonPanel, BorderLayout.SOUTH);

        typeCombo.addActionListener(e -> updateForm.run());
        updateForm.run();

        saveBtn.addActionListener(e -> {
            try {
                entry.setName(nameField.getText());
                entry.setType((GameMappingConfig.MappingType) typeCombo.getSelectedItem());
                entry.setTriggerType((GameMappingConfig.TriggerType) triggerCombo.getSelectedItem());
                entry.setKeyName(keyField.getText());
                entry.setPhoneX(Float.parseFloat(xField.getText()));
                entry.setPhoneY(Float.parseFloat(yField.getText()));
                entry.setMouseSensitivity(Integer.parseInt(sensitivityField.getText()));
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(editDialog, "请输入有效的数字");
                return;
            }
            GameMappingConfig.saveMappings();
            GameMappingConfig.loadMappings();
            editDialog.dispose();
        });

        cancelBtn.addActionListener(e -> editDialog.dispose());

        editDialog.setVisible(true);
        editDialog.toFront();
    }

    private JPanel createRow(String label, JComponent field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        row.add(new JLabel(label));
        row.add(field);
        return row;
    }

    private JLabel createHint(String text) {
        JLabel hint = new JLabel("<html><span style='color:gray;font-size:11px;'>" + text + "</span></html>");
        hint.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        return hint;
    }
}