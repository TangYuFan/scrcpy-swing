package com.tyf.demo.gui;

import com.tyf.demo.entity.ButtonEditor;
import com.tyf.demo.entity.ButtonRenderer;
import com.tyf.demo.entity.Device;
import com.tyf.demo.entity.DeviceTableModel;
import com.tyf.demo.service.ConnectService;
import com.tyf.demo.service.ConstService;
import com.tyf.demo.util.DexTools;
import com.tyf.demo.util.GuiTools;
import org.pmw.tinylog.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @desc : 顶部工具栏
 */
public class TopPanel extends JPanel {

    // 切换手机弹出窗
    JDialog popup;

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
        JLabel readme = GuiTools.createLinkLabel("INFO", ConstService.FONT_NORMAL, ConstService.COLOR_BLACK);
        JLabel log = GuiTools.createLinkLabel("Log", ConstService.FONT_NORMAL, ConstService.COLOR_BLACK);


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
                // 搞个弹出窗，显示一些文本信息
                JOptionPane.showMessageDialog(
                        MainFrame.getMainFrame(),
                        "高DPI下缩放：\n" +
                                "1.右键exe → 属性 → 兼容性 → 更改高DPI设置\n" +
                                "2.勾选覆盖高DPI缩放行为 → 应用程序",
                        "说明",
                        JOptionPane.INFORMATION_MESSAGE
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
                    return;
                }
                // 查询当前手机设备
                List<Device> devices = DexTools.listDevices();
                DeviceTableModel model = new DeviceTableModel(devices);
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
                ButtonEditor be = new ButtonEditor(new JCheckBox(), table, device -> {
                    Logger.info("Open：" + device.toString());
                    popup.dispose();
                    ConnectService.connectDevice(device);
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


        leftPanel.add(swi);
        leftPanel.add(readme);
        rightPanel.add(log);


        // 加入主面板
        add(leftPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.EAST);
    }
}