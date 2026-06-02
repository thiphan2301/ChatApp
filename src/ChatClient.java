import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;

public class ChatClient {
    private Socket socket;
    private PrintWriter writer;
    private String username;
    private JFrame frame;
    private JPanel chatPanel;
    private JScrollPane chatScroll;
    private JTextField inputField;
    private JButton sendButton;
    private JLabel statusLabel;
    private DefaultListModel<String> userListModel;
    private JList<String> userList;
    private Map<String, Color> userColors = new HashMap<>();
    
    // THÊM: Biến lưu danh sách phòng hiện có từ server gửi về
    private List<String> availableRooms = new ArrayList<>();

    public ChatClient(String host, int port) {
        try {
            socket = new Socket(host, port);
            writer = new PrintWriter(socket.getOutputStream(), true);
            createUI();
            authenticate();
            startListener();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "Unable to connect to server",
                    "Connection Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createUI() {
        frame = new JFrame("Modern Chat Client");
        frame.setSize(800, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        
        // --- THÊM: TẠO PANEL PHÍA TRÊN GỒM TRẠNG THÁI VÀ NÚT QUẢN LÝ PHÒNG ---
        JPanel topPanel = new JPanel(new BorderLayout());
        
        statusLabel = new JLabel("Connecting...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        topPanel.add(statusLabel, BorderLayout.CENTER);
        
        JButton roomButton = new JButton("Quản lý Phòng");
        roomButton.setBackground(new Color(60, 179, 113)); // Màu xanh lá
        roomButton.setForeground(Color.WHITE);
        roomButton.setFocusPainted(false);
        roomButton.addActionListener(e -> openRoomManager());
        
        JPanel topButtonPanel = new JPanel();
        topButtonPanel.add(roomButton);
        topPanel.add(topButtonPanel, BorderLayout.EAST);
        
        frame.add(topPanel, BorderLayout.NORTH);
        // -----------------------------------------------------------------

        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(new Color(245,245,245));
        chatScroll = new JScrollPane(chatPanel);
        frame.add(chatScroll, BorderLayout.CENTER);
        
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        sendButton = new JButton("Send");
        sendButton.setBackground(new Color(100,149,237));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        inputPanel.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
        frame.add(inputPanel, BorderLayout.SOUTH);
        
        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("Options");
        JMenuItem clearItem = new JMenuItem("Clear Chat");
        clearItem.addActionListener(e -> {
            chatPanel.removeAll();
            chatPanel.revalidate();
            chatPanel.repaint();
        });
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        menu.add(clearItem);
        menu.add(exitItem);
        menuBar.add(menu);
        frame.setJMenuBar(menuBar);
        frame.setVisible(true);

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setPreferredSize(new Dimension(150, 0));
        
        // Double-click vào tên người dùng trong danh sách để tự điền lệnh nhắn tin riêng
        userList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selectedUser = userList.getSelectedValue();

                    if (selectedUser != null && selectedUser.equals(username)) {
                        addMessageBubble("SYSTEM: Không thể nhắn tin riêng cho chính mình.");
                        return;
                    }

                    if (selectedUser != null) {
                        inputField.setText("/private " + selectedUser + " ");
                        inputField.requestFocus();
                    }
                }
            }
        });

        frame.add(new JScrollPane(userList), BorderLayout.EAST);
    }

    // THÊM: Hàm mở Dialog quản lý phòng (Đã được làm đơn giản lại)
    private void openRoomManager() {
        RoomSelectionDialog dialog = new RoomSelectionDialog(frame, availableRooms);
        dialog.setVisible(true);

        String action = dialog.getActionType();
        if ("JOIN".equals(action)) {
            writer.println("/join " + dialog.getRoomName());
            chatPanel.removeAll(); 
            chatPanel.revalidate();
            chatPanel.repaint();
        } else if ("CREATE".equals(action)) {
            writer.println("/create " + dialog.getRoomName());
            chatPanel.removeAll(); 
            chatPanel.revalidate();
            chatPanel.repaint();
        }
    }

    private void sendMessage() {
        String message = inputField.getText().trim();

        if (message.isEmpty()) {
            return;
        }

        // Xử lý cú pháp nhắn tin riêng từ client
        // Cú pháp: /private tenNguoiNhan noiDung
        if (message.equals("/private") || message.startsWith("/private ")) {
            String[] parts = message.split("\\s+", 3);

            if (parts.length < 3) {
                addMessageBubble("SYSTEM: Vui lòng nhập đúng cú pháp: /private tenNguoiNhan noiDung");
                inputField.setText("");
                return;
            }

            String receiver = parts[1].trim();
            String content = parts[2].trim();

            writer.println("PRIVATE:" + receiver + ":" + content);
            addMessageBubble(username + " -> " + receiver + " (private): " + content);
        } else {
            writer.println(message);
        }

        inputField.setText("");
    }

    private void startListener() {
        Thread listener = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String message;
                while ((message = reader.readLine()) != null) {
                    if (message.startsWith("USERLIST:")) {
                        updateUserList(message.substring(9));
                    } 
                    // THÊM: Xử lý các gói tin về Phòng từ Server
                    else if (message.startsWith("ROOMLIST:")) {
                        String[] rooms = message.substring(9).split(",");
                        availableRooms = new ArrayList<>(Arrays.asList(rooms));
                    } 
                    else if (message.startsWith("ROOM_ERROR:")) {
                        JOptionPane.showMessageDialog(frame, message.substring(11), "Lỗi Phòng", JOptionPane.ERROR_MESSAGE);
                    }
                    else if (message.startsWith("ROOM_CHANGED:") || message.startsWith("IS_HOST:")) {
                        System.out.println(message); // Tạm in ra console để test
                    } 
                    else {
                        addMessageBubble(message);
                    }
                }
            } catch (IOException e) {
                addMessageBubble("Server disconnected.");
                statusLabel.setText("Disconnected");
                inputField.setEnabled(false);
                sendButton.setEnabled(false);
            }
        });
        listener.start();
    }

    private void updateUserList(String listStr) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            String[] users = listStr.split(",");
            for (String u : users) {
                if (!u.isEmpty()) {
                    userListModel.addElement(u);
                    userColors.putIfAbsent(u, generateColor(u));
                }
            }
        });
    }

    private Color generateColor(String name) {
        Random rand = new Random(name.hashCode());
        return new Color(rand.nextInt(200)+30, rand.nextInt(200)+30, rand.nextInt(200)+30);
    }

    private void addMessageBubble(String message) {
        SwingUtilities.invokeLater(() -> {
            JPanel bubblePanel = new JPanel();
            bubblePanel.setLayout(new FlowLayout(username.equals(getSender(message))? FlowLayout.RIGHT: FlowLayout.LEFT));
            bubblePanel.setOpaque(false);
            JTextArea bubble = new JTextArea(message);
            bubble.setLineWrap(true);
            bubble.setWrapStyleWord(true);
            bubble.setEditable(false);
            bubble.setBorder(BorderFactory.createEmptyBorder(5,10,5,10));
            bubble.setBackground(userColors.computeIfAbsent(getSender(message), k -> generateColor(k)));
            bubble.setForeground(Color.WHITE);
            bubble.setOpaque(true);
            bubble.setFont(new Font("Arial", Font.PLAIN, 14));
            bubble.setBorder(new RoundedBorder(15));
            bubblePanel.add(bubble);
            chatPanel.add(bubblePanel);
            chatPanel.revalidate();
            chatScroll.getVerticalScrollBar().setValue(chatScroll.getVerticalScrollBar().getMaximum());
        });
    }

    private String getSender(String message) {
        int idx = message.indexOf(":");
        if (idx > 0) {
            return message.substring(0, idx);
        }
        return "";
    }

    public static void main(String[] args) {
        new ChatClient("localhost", 8000);
    }

    class RoundedBorder implements Border {
        private int radius;
        RoundedBorder(int r) { radius = r; }
        public Insets getBorderInsets(Component c) { return new Insets(radius+1, radius+1, radius+2, radius); }
        public boolean isBorderOpaque() { return false; }
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            g.setColor(Color.BLACK);
            g.drawRoundRect(x, y, width-1, height-1, radius, radius);
        }
    }

    // Hàm xác thực tài khoản (đăng nhập, đăng ký)
    private void authenticate() {
        while (true) {
            JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
            JTextField userField = new JTextField();
            JPasswordField passField = new JPasswordField();
            panel.add(new JLabel("Tên đăng nhập:"));
            panel.add(userField);
            panel.add(new JLabel("Mật khẩu:"));
            panel.add(passField);

            String[] options = new String[]{"Đăng nhập", "Đăng ký", "Thoát"};
            int option = JOptionPane.showOptionDialog(frame, panel, "Xác thực tài khoản",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                    null, options, options[0]);

            if (option == 2 || option == JOptionPane.CLOSED_OPTION) {
                System.exit(0); // Nhấn thoát hoặc dấu X
            }

            String user = userField.getText().trim();
            String pass = new String(passField.getPassword()).trim();
            String command = (option == 0) ? "LOGIN:" : "REG:"; 
            
            // Gửi gói tin lên server theo đúng giao thức đã thiết kế
            writer.println(command + user + ":" + pass);

            try {
                // Đọc phản hồi trực tiếp từ server
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String response = reader.readLine();

                if (response != null && response.equals("LOGIN_SUCCESS")) {
                    this.username = user;
                    statusLabel.setText("Connected as: " + username);
                    JOptionPane.showMessageDialog(frame, "Đăng nhập thành công!");
                    break; // Thoát vòng lặp, bắt đầu chat
                    
                } else if (response != null && response.equals("REG_SUCCESS")) {
                    JOptionPane.showMessageDialog(frame, "Đăng ký thành công! Vui lòng đăng nhập lại.");
                    
                } else if (response != null) {
                    // Tách chuỗi để lấy lý do lỗi (Ví dụ: LOGIN_FAIL:Sai mật khẩu)
                    String[] errorParts = response.split(":", 2);
                    String errorMsg = (errorParts.length > 1) ? errorParts[1] : "Lỗi không xác định";
                    JOptionPane.showMessageDialog(frame, errorMsg, "Thất bại", JOptionPane.ERROR_MESSAGE);
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Mất kết nối tới server!");
                System.exit(0);
            }
        }
    }

    class RoomSelectionDialog extends JDialog {
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
}