package com.group_finity.mascot;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * キャラクター（スキン）を選択するためのウィンドウ。
 */
public class SkinSelectionWindow extends JFrame {

    private final JLabel previewLabel;

    public SkinSelectionWindow(List<String> skins, Consumer<String> onSelected) {
        setTitle("Select Character");
        setSize(300, 320); // プレビュー用に高さを拡張
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // --- 上部: スキン選択 ---
        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        inputPanel.add(new JLabel("Skin:"));
        
        JComboBox<String> skinBox = new JComboBox<>(skins.toArray(new String[0]));
        inputPanel.add(skinBox);
        
        panel.add(inputPanel, BorderLayout.NORTH);

        // --- 中央: プレビュー画像 ---
        previewLabel = new JLabel("No Preview", SwingConstants.CENTER);
        previewLabel.setPreferredSize(new Dimension(128, 128));
        previewLabel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        previewLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        previewLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String selected = (String) skinBox.getSelectedItem();
                onSelected.accept(selected);
                dispose();
            }
        });
        
        JPanel previewPanel = new JPanel(new GridBagLayout());
        previewPanel.add(previewLabel);
        panel.add(previewPanel, BorderLayout.CENTER);

        // --- 下部: 開始ボタン ---
        JButton okButton = new JButton("Start");
        okButton.addActionListener(e -> {
            String selected = (String) skinBox.getSelectedItem();
            onSelected.accept(selected);
            dispose();
        });
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(okButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        // イベントリスナー設定
        skinBox.addActionListener(e -> {
            String selected = (String) skinBox.getSelectedItem();
            updatePreview(selected);
        });

        // 初期表示更新
        if (skinBox.getItemCount() > 0) {
            skinBox.setSelectedIndex(0);
            updatePreview((String) skinBox.getSelectedItem());
        }

        add(panel);
    }

    private void updatePreview(String skinName) {
        File imageFile;
        // "Default" の場合は img 直下、それ以外は img/SkinName/ 配下を参照
        if ("Default".equals(skinName)) {
            imageFile = new File("img/shime1.png");
        } else {
            imageFile = new File("img/" + skinName + "/shime1.png");
        }

        if (imageFile.exists()) {
            try {
                BufferedImage img = ImageIO.read(imageFile);
                // shimeji画像は通常128x128程度なのでそのまま表示
                previewLabel.setIcon(new ImageIcon(img));
                previewLabel.setText("");
            } catch (IOException e) {
                previewLabel.setIcon(null);
                previewLabel.setText("Error");
                e.printStackTrace();
            }
        } else {
            previewLabel.setIcon(null);
            previewLabel.setText("No Image");
        }
    }
}