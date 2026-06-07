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
    private BufferedReader serverReader; // Giữ luồng đọc cho cả authenticate và listener
    private String username;
    private JFrame frame;
    private JPanel chatPanel;
    private JScrollPane chatScroll;
    private JTextField inputField;
    private JButton sendButton;
    private JButton settingsButton; // Nút dành riêng điều khiển cấu hình phòng cho Host
    private JLabel statusLabel;
    private DefaultListModel<String> userListModel;
    private JList<String> userList;
    private Map<String, Color> userColors = new HashMap<>();
    
    private List<String> availableRooms = new ArrayList<>();

    public ChatClient(String host, int port) {
        try {
            socket = new Socket(host, port);
            writer = new PrintWriter(socket.getOutputStream(), true);
            serverReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // 1. Tạo giao diện trước
            createUI();
            // 2. Ép buộc xác thực tài khoản (Vòng lặp chặn cho đến khi LOGIN_SUCCESS)
            authenticate();
            // 3. Khởi chạy luồng lắng nghe server liên tục
            startListener();
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "Unable to connect to server",
                    "Connection Error",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
    }

    private void createUI() {
        frame = new JFrame("Modern Chat Client");
        frame.setSize(850, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        statusLabel = new JLabel("Connecting...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        frame.add(statusLabel, BorderLayout.NORTH);

        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(new Color(245, 245, 245));
        chatScroll = new JScrollPane(chatPanel);
        frame.add(chatScroll, BorderLayout.CENTER);

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setPreferredSize(new Dimension(150, 0));
        
        // Double-click vào tên người dùng trong danh sách để tự điền lệnh nhắn tin riêng
        userList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
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

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        
        // Thiết lập panel chức năng 3 nút: Rooms - Settings - Send
        JPanel buttonPanel = new JPanel(new GridLayout(1, 3, 5, 0));

        JButton roomButton = new JButton("Rooms");
        roomButton.setBackground(new Color(60, 179, 113));
        roomButton.setForeground(Color.WHITE);
        roomButton.setFocusPainted(false);
        roomButton.addActionListener(e -> {
            // Lưu ý: Đảm bảo class RoomSelectionDialog tồn tại trong project của bạn
            RoomSelectionDialog dialog = new RoomSelectionDialog(frame, availableRooms);
            dialog.setVisible(true);
            
            if (dialog.getActionType() != null) {
                if (dialog.getActionType().equals("CREATE")) {
                    writer.println("/create " + dialog.getRoomName() + "|" + dialog.getRoomKey() + "|" + dialog.isAllowHistory() + "|" + dialog.isAllowNewJoins());
                } else if (dialog.getActionType().equals("JOIN")) {
                    writer.println("/join " + dialog.getRoomName() + "|" + dialog.getRoomKey());
                }
            }
        });

        // Thiết lập nút Settings ban đầu ẩn (chỉ bật khi nhận phản hồi từ server chứng minh mình là Host)
        settingsButton = new JButton("Settings");
        settingsButton.setEnabled(false);
        settingsButton.setBackground(new Color(218, 165, 32));
        settingsButton.setForeground(Color.WHITE);
        settingsButton.setFocusPainted(false);
        settingsButton.addActionListener(e -> {
            JCheckBox chkHist = new JCheckBox("Cho phép người mới xem lịch sử chat trước đó", true);
            JCheckBox chkJoin = new JCheckBox("Cho phép người mới tham gia vào phòng", true);
            Object[] optionMessage = { chkHist, chkJoin };
            int option = JOptionPane.showConfirmDialog(frame, optionMessage, "Cài đặt phòng (Chủ phòng)", JOptionPane.OK_CANCEL_OPTION);
            if (option == JOptionPane.OK_OPTION) {
                writer.println("/settings " + chkHist.isSelected() + "|" + chkJoin.isSelected());
            }
        });

        sendButton = new JButton("Send");
        sendButton.setBackground(new Color(100, 149, 237));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());

        buttonPanel.add(roomButton);
        buttonPanel.add(settingsButton);
        buttonPanel.add(sendButton);

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);
        inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
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
    }

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
                System.exit(0);
            }

            String user = userField.getText().trim();
            String pass = new String(passField.getPassword()).trim();
            
            if (user.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Tài khoản và mật khẩu không được để trống!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            
            String command = (option == 0) ? "LOGIN:" : "REG:"; 
            
            // Gửi gói tin lên server theo đúng giao thức
            writer.println(command + user + ":" + pass);

            try {
                // Đọc phản hồi đồng bộ trực tiếp từ server
                String response = serverReader.readLine();

                if (response != null && response.equals("LOGIN_SUCCESS")) {
                    this.username = user;
                    statusLabel.setText("Connected as: " + username);
                    JOptionPane.showMessageDialog(frame, "Đăng nhập thành công!");
                    break; // Thoát vòng lặp, chuyển sang giao diện chat chính
                    
                } else if (response != null && response.equals("REG_SUCCESS")) {
                    JOptionPane.showMessageDialog(frame, "Đăng ký thành công! Vui lòng đăng nhập lại.");
                    
                } else if (response != null) {
                    String[] errorParts = response.split(":", 2);
                    String errorMsg = (errorParts.length > 1) ? errorParts[1] : "Lỗi không xác định";
                    JOptionPane.showMessageDialog(frame, errorMsg, "Thất bại", JOptionPane.ERROR_MESSAGE);
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Mất kết nối tới server trong lúc xác thực!");
                System.exit(0);
            }
        }
    }

    private void sendMessage() {
        String message = inputField.getText().trim();

        if (message.isEmpty()) {
            return;
        }

        // Xử lý cú pháp nhắn tin riêng từ client: /private tenNguoiNhan noiDung
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
            // Tin nhắn phòng thông thường hoặc lệnh dạng ẩn khác
            writer.println(message);
        }

        inputField.setText("");
    }

    private void startListener() {
        Thread listener = new Thread(() -> {
            try {
                String message;
                // Sử dụng tiếp luồng serverReader đã khởi tạo ở Constructor
                while ((message = serverReader.readLine()) != null) {
                    if (message.startsWith("USERLIST:")) {
                        updateUserList(message.substring(9));
                    } 
                    else if (message.startsWith("ROOMLIST:")) {
                        String[] rooms = message.substring(9).split(",");
                        availableRooms = Arrays.asList(rooms);
                    }
                    else if (message.startsWith("ROOM_CHANGED:")) {
                        String currentRoom = message.substring(13);
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("Connected as: " + username + " | Room: " + currentRoom);
                            chatPanel.removeAll();
                            chatPanel.revalidate();
                            chatPanel.repaint();
                        });
                    }
                    else if (message.startsWith("IS_HOST:")) {
                        boolean isHost = Boolean.parseBoolean(message.substring(8));
                        SwingUtilities.invokeLater(() -> {
                            settingsButton.setEnabled(isHost); // Bật/Tắt nút tùy quyền Host
                        });
                    }
                    else if (message.startsWith("ROOM_ERROR:")) {
                        String errorMsg = message.substring(11);
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(frame, errorMsg, "Lỗi phòng", JOptionPane.ERROR_MESSAGE);
                        });
                    }
                    else {
                        addMessageBubble(message);
                    }
                }
            } catch (IOException e) {
                addMessageBubble("Server disconnected.");
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Disconnected");
                    inputField.setEnabled(false); 
                    sendButton.setEnabled(false); 
                    settingsButton.setEnabled(false);
                });
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
        return new Color(rand.nextInt(200) + 30, rand.nextInt(200) + 30, rand.nextInt(200) + 30);
    }

    private void addMessageBubble(String message) {
        SwingUtilities.invokeLater(() -> {
            JPanel bubblePanel = new JPanel();
            bubblePanel.setLayout(new FlowLayout(username.equals(getSender(message)) ? FlowLayout.RIGHT : FlowLayout.LEFT));
            bubblePanel.setOpaque(false);
            
            JTextArea bubble = new JTextArea(message);
            bubble.setLineWrap(true); 
            bubble.setWrapStyleWord(true); 
            bubble.setEditable(false);
            bubble.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
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
        // Xử lý loại trừ trường hợp nhãn hệ thống hoặc định dạng đặc biệt gửi từ Server
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
        public Insets getBorderInsets(Component c) { return new Insets(radius + 1, radius + 1, radius + 2, radius); }
        public boolean isBorderOpaque() { return false; }
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            g.setColor(Color.BLACK); 
            g.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
        }
    }
}