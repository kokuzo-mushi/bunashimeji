package com.group_finity.mascot;

import com.group_finity.mascot.behavior.Behavior;
import com.group_finity.mascot.config.xml.XmlBehavior;
import com.group_finity.mascot.config.xml.XmlBehaviors;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * ビヘイビアの設定（頻度など）を動的に変更するためのウィンドウ。
 */
public class SettingsWindow extends JFrame {

    private final List<Behavior> behaviors;
    private final DefaultTableModel model;
    private final JTable table;
    private final Set<Integer> changedRows = new HashSet<>();

    public SettingsWindow(
            List<Behavior> behaviors,
            List<String> skins,
            String currentSkin,
            Consumer<String> onSkinChange,
            int initialGravity,
            Consumer<Integer> onGravityChange,
            double initialTimeScale,
            Consumer<Double> onTimeScaleChange,
            Runnable onLimitToActiveWindow,
            Runnable onResetLimit
    ) {
        this.behaviors = behaviors;
        setTitle("設定 - Action Frequency");
        setSize(500, 550); // UIが増えたので高さを拡張
        setLocationRelativeTo(null);
        // 常に手前に表示して、マスコットに隠れないようにする
        setAlwaysOnTop(true);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // --- Control Panel (Skin, Gravity, Speed) ---
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));

        // 1. Skin Selection
        JPanel skinPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        skinPanel.add(new JLabel("Skin:"));
        JComboBox<String> skinBox = new JComboBox<>(skins.toArray(new String[0]));
        skinBox.setSelectedItem(currentSkin);
        skinBox.addActionListener(e -> {
            String selected = (String) skinBox.getSelectedItem();
            onSkinChange.accept(selected);
        });
        skinPanel.add(skinBox);
        controlPanel.add(skinPanel);

        // 2. Gravity Slider
        JPanel gravityPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        gravityPanel.add(new JLabel("Gravity:"));
        JSlider gravitySlider = new JSlider(0, 5, initialGravity);
        gravitySlider.setMajorTickSpacing(1);
        gravitySlider.setPaintTicks(true);
        gravitySlider.setPaintLabels(true);
        gravitySlider.addChangeListener(e -> {
            int val = gravitySlider.getValue();
            onGravityChange.accept(val);
        });
        gravityPanel.add(gravitySlider);
        controlPanel.add(gravityPanel);

        // 3. Speed Slider (Time Scale)
        JPanel speedPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        speedPanel.add(new JLabel("Speed:"));
        // 10% to 300% (Default 100%)
        int initialSpeed = (int) (initialTimeScale * 100);
        JSlider speedSlider = new JSlider(10, 300, initialSpeed);
        speedSlider.setMajorTickSpacing(50);
        speedSlider.setMinorTickSpacing(10);
        speedSlider.setPaintTicks(true);
        speedSlider.setPaintLabels(true);
        
        // ラベルのカスタマイズ
        Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
        labelTable.put(100, new JLabel("1.0x"));
        labelTable.put(200, new JLabel("2.0x"));
        labelTable.put(300, new JLabel("3.0x"));
        speedSlider.setLabelTable(labelTable);

        speedSlider.addChangeListener(e -> {
            double val = speedSlider.getValue() / 100.0;
            onTimeScaleChange.accept(val);
        });
        speedPanel.add(speedSlider);
        controlPanel.add(speedPanel);

        // 4. Window Limit
        JPanel limitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        limitPanel.add(new JLabel("Limit:"));
        
        JButton limitBtn = new JButton("Active Window (3s)");
        limitBtn.setToolTipText("Click and switch to target window within 3 seconds.");
        limitBtn.addActionListener(e -> {
            onLimitToActiveWindow.run();
            limitBtn.setText("Wait 3s...");
            new Timer(3000, evt -> limitBtn.setText("Active Window (3s)")).start();
        });
        limitPanel.add(limitBtn);

        JButton resetLimitBtn = new JButton("Reset");
        resetLimitBtn.addActionListener(e -> onResetLimit.run());
        limitPanel.add(resetLimitBtn);
        controlPanel.add(limitPanel);

        add(controlPanel, BorderLayout.NORTH);

        // テーブルモデルの作成
        String[] columnNames = {"Behavior Name", "Frequency", "Hidden"};
        this.model = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // Frequency(1) と Hidden(2) を編集可能にする
                return column == 1 || column == 2;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                // Hidden列はチェックボックスを表示するためにBooleanクラスを返す
                return columnIndex == 2 ? Boolean.class : Object.class;
            }
        };

        // データの投入
        for (Behavior behavior : behaviors) {
            model.addRow(new Object[]{behavior.getName(), behavior.getFrequency(), behavior.isHidden()});
        }

        this.table = new JTable(model) {
            @Override
            public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row)) {
                    if (changedRows.contains(row)) {
                        c.setBackground(new Color(255, 255, 224)); // 薄い黄色
                    } else {
                        c.setBackground(getBackground());
                    }
                }
                return c;
            }
        };
        
        // 編集確定時のリスナー
        model.addTableModelListener(e -> {
            if (e.getType() == javax.swing.event.TableModelEvent.UPDATE) {
                int row = e.getFirstRow();
                int col = e.getColumn();
                if (row < 0 || row >= behaviors.size()) return;

                Behavior behavior = behaviors.get(row);                
                if (col == 1) {
                    try {
                        Object value = model.getValueAt(row, col);
                        int newFrequency = Integer.parseInt(value.toString());
                        
                        // Behaviorの頻度を更新
                        behavior.setFrequency(newFrequency);
                        System.out.println("[Settings] Updated " + behavior.getName() + " frequency to " + newFrequency);
                        changedRows.add(row);
                        table.repaint();
                    } catch (Exception ex) {
                        System.err.println("[Settings] Failed to update frequency: " + ex.getMessage());
                    }
                } else if (col == 2) {
                    try {
                        Boolean newHidden = (Boolean) model.getValueAt(row, col);
                        behavior.setHidden(newHidden);
                        System.out.println("[Settings] Updated " + behavior.getName() + " hidden to " + newHidden);
                        changedRows.add(row);
                        table.repaint();
                    } catch (Exception ex) {
                        System.err.println("[Settings] Failed to update hidden: " + ex.getMessage());
                    }
                }
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);

        // 保存ボタンの追加
        JButton saveButton = new JButton("Save to behaviors.xml");
        saveButton.addActionListener(e -> saveSettings());
        
        JButton reloadButton = new JButton("Reload from XML");
        reloadButton.addActionListener(e -> reloadSettings());

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(saveButton);
        buttonPanel.add(reloadButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void saveSettings() {
        try {
            // 編集中であれば強制的に確定させる
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }

            File file = new File("conf/behaviors.xml");
            if (!file.exists()) {
                JOptionPane.showMessageDialog(this, "behaviors.xml not found!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            JAXBContext context = JAXBContext.newInstance(XmlBehaviors.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            XmlBehaviors xmlBehaviors = (XmlBehaviors) unmarshaller.unmarshal(file);

            // メモリ上の設定をXMLモデルに反映
            for (XmlBehavior xb : xmlBehaviors.getBehaviors()) {
                for (Behavior b : behaviors) {
                    if (b.getName().equals(xb.getName())) {
                        xb.setFrequency(b.getFrequency());
                        xb.setHidden(b.isHidden());
                        break;
                    }
                }
            }

            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            marshaller.marshal(xmlBehaviors, file);

            JOptionPane.showMessageDialog(this, "Settings saved successfully!");
            changedRows.clear();
            table.repaint();
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to save settings:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void reloadSettings() {
        try {
            // 編集中であれば強制的に確定させる
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }

            File file = new File("conf/behaviors.xml");
            if (!file.exists()) {
                JOptionPane.showMessageDialog(this, "behaviors.xml not found!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            JAXBContext context = JAXBContext.newInstance(XmlBehaviors.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            XmlBehaviors xmlBehaviors = (XmlBehaviors) unmarshaller.unmarshal(file);

            // メモリ上の設定をXMLモデルから反映
            for (XmlBehavior xb : xmlBehaviors.getBehaviors()) {
                for (Behavior b : behaviors) {
                    if (b.getName().equals(xb.getName())) {
                        if (xb.getFrequency() != null) {
                            b.setFrequency(xb.getFrequency());
                            b.setHidden(xb.isHidden());
                            // テーブルの表示も更新
                            for (int i = 0; i < model.getRowCount(); i++) {
                                if (model.getValueAt(i, 0).equals(b.getName())) {
                                    model.setValueAt(b.getFrequency(), i, 1);
                                    model.setValueAt(b.isHidden(), i, 2);
                                    break;
                                }
                            }
                        }
                        break;
                    }
                }
            }
            JOptionPane.showMessageDialog(this, "Settings reloaded successfully!");
            changedRows.clear();
            table.repaint();
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to reload settings:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}