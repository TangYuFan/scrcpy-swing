package com.tyf.demo.service.mapping;

import com.tyf.demo.gui.ContentPanel;
import com.tyf.demo.gui.MainPanel;
import com.tyf.demo.service.GameMappingConfig;
import com.tyf.demo.service.GameMappingConfig.MappingEntry;
import com.tyf.demo.service.GameMappingConfig.MappingType;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.FlowLayout;

public final class MappingEditUiFactory {

    private static final int FIELD_MAX_W = 100;
    private static final int DIALOG_MAX_W = 440;

    private MappingEditUiFactory() {}

    public static void showEditDialog(Window owner, MappingEntry entry, Runnable onSaved) {
        AbstractBuiltinMapping def = BuiltinMappingRegistry.byId(entry.getBuiltinId());
        if (def == null) {
            def = BuiltinMappingRegistry.byId(entry.getId());
        }
        if (def == null) {
            JOptionPane.showMessageDialog(owner, "未知映射项，无法编辑", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog dlg = new JDialog(owner, "配置 · " + def.getDisplayName(), Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setResizable(false);
        dlg.getContentPane().setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridBagLayout());
        Border pad = BorderFactory.createEmptyBorder(8, 10, 8, 10);
        form.setBorder(pad);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 0, 4, 0);
        gbc.anchor = GridBagConstraints.WEST;
        int row = 0;

        JLabel hint = new JLabel("<html><div style='width:" + FIELD_MAX_W + "px;'>" +
                "<div style='color:#333;font-size:12px;font-weight:bold;'>" + escapeForHtml(def.getHelpTitle()) + "</div>" +
                "<div style='color:gray;font-size:11px;margin-top:4px;'>" + escapeForHtml(def.getHelpBody()) + "</div>" +
                "</div></html>");
        hint.setVerticalAlignment(SwingConstants.TOP);
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        form.add(hint, gbc);
        row++;

        JLabel nameLabel = new JLabel(def.getDisplayName());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        addRow(form, gbc, row++, "映射项:", nameLabel);

        MappingType type = entry.getType();
        JTextField xField = new JTextField(String.valueOf(entry.getPhoneX()), 10);
        JTextField yField = new JTextField(String.valueOf(entry.getPhoneY()), 10);
        JTextField rField = new JTextField(String.valueOf(entry.getJoystickRadius()), 8);
        JTextField sensField = new JTextField(String.valueOf(entry.getMouseSensitivity()), 6);
        JTextField keyField = new JTextField(
                entry.getKeyName() != null ? entry.getKeyName() : KeyMappingUtil.keyCodeToDisplay(entry.getKeyCode()), 12);

        if (type == MappingType.JOYSTICK_WASD) {
            addRowWithPicker(form, gbc, row++, "摇杆中心 X(0~1):", xField, yField);
            addRow(form, gbc, row++, "摇杆中心 Y(0~1):", yField);
            addRow(form, gbc, row++, "最大偏移系数:", rField);
            JLabel tip = new JLabel("<html><span style='color:gray;font-size:11px;'>相对屏幕短边，建议 0.08~0.2</span></html>");
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.gridwidth = 2;
            form.add(tip, gbc);
            row++;
        } else if (type == MappingType.MOUSE_MOVE) {
            addRow(form, gbc, row++, "灵敏度 (1~10):", sensField);
            JLabel tip = new JLabel("<html><span style='color:gray;font-size:11px;'>建议 3~5，值越大视角移动越快</span></html>");
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.gridwidth = 2;
            form.add(tip, gbc);
            row++;
        } else if (type == MappingType.CLICK) {
            if (entry.getTriggerType() == GameMappingConfig.TriggerType.KEYBOARD) {
                addRow(form, gbc, row++, "键盘键位:", keyField);
                JLabel kt = new JLabel("<html><span style='color:gray;font-size:11px;'>如 A、空格填 SPACE、F1 等</span></html>");
                gbc.gridx = 0;
                gbc.gridy = row;
                gbc.gridwidth = 2;
                form.add(kt, gbc);
                row++;
            }
            addRowWithPicker(form, gbc, row++, "屏幕坐标 (X, Y):", xField, yField);
        }

        limitW(xField);
        limitW(yField);
        limitW(rField);
        limitW(sensField);
        limitW(keyField);

        if (xField.getParent() instanceof JPanel) {
            ((JPanel) xField.getParent()).revalidate();
        }

        JScrollPane scroll = new JScrollPane(form,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 2));
        dlg.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        JButton ok = new JButton("保存");
        JButton cancel = new JButton("取消");
        bottom.add(ok);
        bottom.add(cancel);
        dlg.add(bottom, BorderLayout.SOUTH);

        ok.addActionListener(e -> {
            try {
                applyToEntry(entry, type, xField, yField, rField, sensField, keyField);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(dlg,
                        ex.getMessage() != null ? ex.getMessage() : "请输入有效数字",
                        "输入错误",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            GameMappingConfig.saveMappings();
            GameMappingConfig.loadMappings();
            GameMappingConfig.ensureBuiltinMappings();
            dlg.dispose();
            if (onSaved != null) {
                onSaved.run();
            }
        });
        cancel.addActionListener(e -> dlg.dispose());

        dlg.pack();
        Dimension pref = dlg.getPreferredSize();
        int w = Math.min(DIALOG_MAX_W, pref.width);
        int h = Math.min(520, pref.height);
        dlg.setSize(w, h);
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
    }

    private static void applyToEntry(MappingEntry entry, MappingType type,
            JTextField xField, JTextField yField, JTextField rField,
            JTextField sensField, JTextField keyField) {
        if (type == MappingType.JOYSTICK_WASD) {
            entry.setPhoneX(parse01(xField.getText()));
            entry.setPhoneY(parse01(yField.getText()));
            entry.setJoystickRadius(parsePositive(rField.getText(), 1.0f));
        } else if (type == MappingType.MOUSE_MOVE) {
            entry.setMouseSensitivity(Integer.parseInt(sensField.getText().trim()));
        } else if (type == MappingType.CLICK) {
            if (entry.getTriggerType() == GameMappingConfig.TriggerType.KEYBOARD) {
                String kn = keyField.getText().trim();
                int kc = KeyMappingUtil.parseKeyString(kn);
                if (kc == 0) {
                    throw new IllegalArgumentException("请填写有效的键盘键位（如 C、SPACE、F1）");
                }
                entry.setKeyCode(kc);
                entry.setKeyName(KeyMappingUtil.keyCodeToDisplay(kc));
            }
            entry.setPhoneX(parse01(xField.getText()));
            entry.setPhoneY(parse01(yField.getText()));
        }
    }

    private static float parse01(String s) {
        float v = Float.parseFloat(s.trim());
        if (v < 0 || v > 1) {
            throw new IllegalArgumentException("坐标须在 0~1 之间（相对屏幕比例）");
        }
        return v;
    }

    private static float parsePositive(String s, float max) {
        float v = Float.parseFloat(s.trim());
        if (v <= 0 || v > max) {
            throw new IllegalArgumentException("半径/系数须为 0~" + max + " 之间的正数");
        }
        return v;
    }

    private static void addRow(JPanel p, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        JLabel lb = new JLabel(label);
        lb.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        p.add(lb, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        
        JPanel fieldPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        field.setPreferredSize(new Dimension(60, 22));
        field.setMaximumSize(new Dimension(80, 22));
        fieldPanel.add(field);
        p.add(fieldPanel, gbc);
    }

    private static void limitW(JComponent c) {
        if (c == null) {
            return;
        }
        Dimension p = c.getPreferredSize();
        int h = p.height > 0 ? p.height : 22;
        c.setMaximumSize(new Dimension(100, h));
    }

    private static void addRowWithPicker(JPanel p, GridBagConstraints gbc, int row, String label, JTextField field, JTextField otherField) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        JLabel lb = new JLabel(label);
        lb.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        p.add(lb, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JPanel fieldPanel = new JPanel(new BorderLayout(4, 0));
        field.setPreferredSize(new Dimension(60, 22));
        field.setMaximumSize(new Dimension(80, 22));
        fieldPanel.add(field, BorderLayout.CENTER);
        
        if (otherField != null) {
            otherField.setPreferredSize(new Dimension(60, 22));
            otherField.setMaximumSize(new Dimension(80, 22));
            fieldPanel.add(otherField, BorderLayout.WEST);
            JButton pickBtn = new JButton("📍");
            pickBtn.setFont(pickBtn.getFont().deriveFont(Font.PLAIN, 12));
            pickBtn.setPreferredSize(new Dimension(28, 22));
            pickBtn.setToolTipText("点击在手机画面上拾取坐标");
            pickBtn.addActionListener(e -> showPickerDialog(field, otherField));
            fieldPanel.add(pickBtn, BorderLayout.EAST);
        }

        p.add(fieldPanel, gbc);
    }

    private static void showPickerDialog(JTextField xField, JTextField yField) {
        ContentPanel cp = MainPanel.getContentPanel();
        if (cp == null || !cp.hasValidFrame()) {
            JOptionPane.showMessageDialog(null, "手机画面未就绪，无法采集坐标", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        BufferedImage frame = cp.getCurrentFrame();
        int imgW = frame.getWidth();
        int imgH = frame.getHeight();

        JDialog pickerDlg = new JDialog((Frame) null, "拾取坐标 - 点击手机画面", true);
        pickerDlg.setUndecorated(true);
        pickerDlg.setAlwaysOnTop(true);

        JPanel container = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int pw = getWidth();
                int ph = getHeight();
                double scale = Math.min(1.0, Math.min((double) pw / imgW, (double) ph / imgH));
                int scaledW = (int) (imgW * scale);
                int scaledH = (int) (imgH * scale);
                int dx = (pw - scaledW) / 2;
                int dy = (ph - scaledH) / 2;
                g.drawImage(frame, dx, dy, scaledW, scaledH, null);
            }
        };

        container.setBackground(Color.BLACK);
        container.setLayout(new BorderLayout());

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(0, 0, 0, 180));
        JLabel tip = new JLabel("点击手机画面上的位置进行拾取，右键或ESC取消");
        tip.setForeground(Color.WHITE);
        tip.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        topBar.add(tip, BorderLayout.CENTER);
        container.add(topBar, BorderLayout.NORTH);

        final int[] picked = {-1, -1};
        final boolean[] confirmed = {false};

        container.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int pw = container.getWidth();
                int ph = container.getHeight();
                double scale = Math.min(1.0, Math.min((double) pw / imgW, (double) ph / imgH));
                int scaledW = (int) (imgW * scale);
                int scaledH = (int) (imgH * scale);
                int dx = (pw - scaledW) / 2;
                int dy = (ph - scaledH) / 2;

                int clickX = e.getX();
                int clickY = e.getY();

                if (e.getButton() == MouseEvent.BUTTON3) {
                    pickerDlg.dispose();
                    return;
                }

                if (clickX >= dx && clickX < dx + scaledW && clickY >= dy && clickY < dy + scaledH) {
                    int imgX = (int) ((clickX - dx) / scale);
                    int imgY = (int) ((clickY - dy) / scale);
                    float ratioX = (float) imgX / imgW;
                    float ratioY = (float) imgY / imgH;
                    xField.setText(String.format("%.2f", ratioX));
                    yField.setText(String.format("%.2f", ratioY));
                    pickerDlg.dispose();
                }
            }
        });

        container.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                    pickerDlg.dispose();
                }
            }
        });

        pickerDlg.setContentPane(container);
        pickerDlg.setSize(Math.min(800, imgW + 50), Math.min(600, imgH + 50));
        pickerDlg.setLocationRelativeTo(null);
        pickerDlg.setVisible(true);
    }

    private static String escapeForHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
