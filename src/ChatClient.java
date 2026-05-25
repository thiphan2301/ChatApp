import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
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
    public ChatClient(String host, int port) {
        try {
            socket = new Socket(host, port);
            writer = new PrintWriter(socket.getOutputStream(), true);
            createUI();
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
        statusLabel = new JLabel("Connecting...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
        frame.add(statusLabel, BorderLayout.NORTH);
        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(new Color(245,245,245));
        chatScroll = new JScrollPane(chatPanel);
        frame.add(chatScroll, BorderLayout.CENTER);
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setPreferredSize(new Dimension(150, 0));
        frame.add(new JScrollPane(userList), BorderLayout.EAST);
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
        clearItem.addActionListener(e -> chatPanel.removeAll());
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        menu.add(clearItem);
        menu.add(exitItem);
        menuBar.add(menu);
        frame.setJMenuBar(menuBar);
        frame.setVisible(true);
        username = JOptionPane.showInputDialog(frame, "Enter your username:");
        writer.println(username);
        statusLabel.setText("Connected as: " + username);
    }
    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty()) {
            writer.println(message); // send to server
            inputField.setText("");
        }
    }
    private void startListener() {
        Thread listener = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String message;
                while ((message = reader.readLine()) != null) {
                    if (message.startsWith("USERLIST:")) {
                        updateUserList(message.substring(9));
                    } else {
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
}


