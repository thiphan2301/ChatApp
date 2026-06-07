import javax.swing.*;
import java.awt.*;
import java.util.List;

public class RoomSelectionDialog extends JDialog {
    private String actionType = null; // "JOIN" or "CREATE"
    private String roomName = null;
    private String roomKey = null;
    private boolean allowHistory = true;
    private boolean allowNewJoins = true;

    public RoomSelectionDialog(JFrame parent, List<String> availableRooms) {
        super(parent, "Room Management", true);
        setLayout(new BorderLayout());
        setSize(420, 260);
        setLocationRelativeTo(parent);

        JTabbedPane tabbedPane = new JTabbedPane();

        // --- TAB 1: JOIN EXISTING ROOM ---
        JPanel joinPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        joinPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        joinPanel.add(new JLabel("Select a room:"));
        JComboBox<String> roomCombo = new JComboBox<>(availableRooms.toArray(new String[0]));
        joinPanel.add(roomCombo);
        
        joinPanel.add(new JLabel("Room Key (Password):"));
        JPasswordField joinKeyField = new JPasswordField();
        joinPanel.add(joinKeyField);
        
        JButton btnJoin = new JButton("Join Room");
        btnJoin.setBackground(new Color(100, 149, 237));
        btnJoin.setForeground(Color.WHITE);
        btnJoin.setFocusPainted(false);
        joinPanel.add(new JLabel()); // Spacer
        joinPanel.add(btnJoin);

        btnJoin.addActionListener(e -> {
            String selected = (String) roomCombo.getSelectedItem();
            if (selected != null && !selected.trim().isEmpty()) {
                actionType = "JOIN";
                roomName = selected.trim();
                roomKey = new String(joinKeyField.getPassword()).trim();
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Please select a room!", "Warning", JOptionPane.WARNING_MESSAGE);
            }
        });

        // --- TAB 2: CREATE NEW ROOM ---
        JPanel createPanel = new JPanel(new GridLayout(5, 2, 8, 8));
        createPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        createPanel.add(new JLabel("New Room Name:"));
        JTextField createNameField = new JTextField();
        createPanel.add(createNameField);
        
        createPanel.add(new JLabel("Set Room Key:"));
        JPasswordField createKeyField = new JPasswordField();
        createPanel.add(createKeyField);
        
        JCheckBox chkHistory = new JCheckBox("Allow chat history", true);
        JCheckBox chkJoins = new JCheckBox("Allow new joins", true);
        createPanel.add(chkHistory);
        createPanel.add(chkJoins);
        
        JButton btnCreate = new JButton("Create & Join");
        btnCreate.setBackground(new Color(60, 179, 113));
        btnCreate.setForeground(Color.WHITE);
        btnCreate.setFocusPainted(false);
        createPanel.add(new JLabel()); // Spacer
        createPanel.add(btnCreate);

        btnCreate.addActionListener(e -> {
            String name = createNameField.getText().trim();
            if (!name.isEmpty()) {
                if (name.contains("|") || name.contains(":")) {
                    JOptionPane.showMessageDialog(this, "Room name cannot contain '|' or ':'", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                actionType = "CREATE";
                roomName = name;
                roomKey = new String(createKeyField.getPassword()).trim();
                allowHistory = chkHistory.isSelected();
                allowNewJoins = chkJoins.isSelected();
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Room name cannot be empty!", "Warning", JOptionPane.WARNING_MESSAGE);
            }
        });

        tabbedPane.addTab("Join Room", joinPanel);
        tabbedPane.addTab("Create Room", createPanel);
        add(tabbedPane, BorderLayout.CENTER);
    }

    public String getActionType() { return actionType; }
    public String getRoomName() { return roomName; }
    public String getRoomKey() { return roomKey; }
    public boolean isAllowHistory() { return allowHistory; }
    public boolean isAllowNewJoins() { return allowNewJoins; }
}