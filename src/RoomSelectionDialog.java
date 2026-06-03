import java.awt.*;
import java.util.List;
import javax.swing.*;

public class RoomSelectionDialog extends JDialog {
    private String actionType = null; // "JOIN" or "CREATE"
    private String roomName = null;

    public RoomSelectionDialog(JFrame parent, List<String> availableRooms) {
        super(parent, "Quản lý Phòng", true);
        setLayout(new BorderLayout());
        setSize(320, 150); // Thu nhỏ kích thước cửa sổ lại cho gọn
        setLocationRelativeTo(parent);

        JTabbedPane tabbedPane = new JTabbedPane();

        // --- TAB 1: JOIN EXISTING ROOM ---
        JPanel joinPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        joinPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        
        JPanel joinComboPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        joinComboPanel.add(new JLabel("Chọn phòng:"));
        JComboBox<String> roomCombo = new JComboBox<>(availableRooms.toArray(new String[0]));
        joinComboPanel.add(roomCombo);
        joinPanel.add(joinComboPanel);
        
        JButton btnJoin = new JButton("Vào Phòng");
        btnJoin.setBackground(new Color(100, 149, 237));
        btnJoin.setForeground(Color.WHITE);
        btnJoin.setFocusPainted(false);
        JPanel joinBtnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        joinBtnPanel.add(btnJoin);
        joinPanel.add(joinBtnPanel);

        btnJoin.addActionListener(e -> {
            String selected = (String) roomCombo.getSelectedItem();
            if (selected != null && !selected.trim().isEmpty()) {
                actionType = "JOIN";
                roomName = selected.trim();
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn một phòng!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            }
        });

        // --- TAB 2: CREATE NEW ROOM ---
        JPanel createPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        createPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        
        JPanel createInputPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        createInputPanel.add(new JLabel("Tên phòng mới:"));
        JTextField createNameField = new JTextField(12);
        createInputPanel.add(createNameField);
        createPanel.add(createInputPanel);
        
        JButton btnCreate = new JButton("Tạo & Vào Phòng");
        btnCreate.setBackground(new Color(60, 179, 113));
        btnCreate.setForeground(Color.WHITE);
        btnCreate.setFocusPainted(false);
        JPanel createBtnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        createBtnPanel.add(btnCreate);
        createPanel.add(createBtnPanel);

        btnCreate.addActionListener(e -> {
            String name = createNameField.getText().trim();
            if (!name.isEmpty()) {
                if (name.contains("|") || name.contains(":")) {
                    JOptionPane.showMessageDialog(this, "Tên phòng không được chứa ký tự đặc biệt '|' hoặc ':'", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                actionType = "CREATE";
                roomName = name;
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Tên phòng không được để trống!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            }
        });

        tabbedPane.addTab("Vào Phòng", joinPanel);
        tabbedPane.addTab("Tạo Phòng Mới", createPanel);
        add(tabbedPane, BorderLayout.CENTER);
    }

    public String getActionType() { return actionType; }
    public String getRoomName() { return roomName; }
}