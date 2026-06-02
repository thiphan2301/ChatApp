import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.Desktop;
import javax.swing.ImageIcon;
import java.nio.file.Files;
import java.util.Base64;
import java.io.File;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class ChatClient {
	private Socket socket;
	private PrintWriter writer;
	private String username;
	private JFrame frame;
	private JPanel chatPanel;
	private JScrollPane chatScroll;
	private JTextField inputField;
	private JButton sendButton;
	private JButton attachButton;
	private JLabel statusLabel;
	private DefaultListModel<String> userListModel;
	private JList<String> userList;
	private Map<String, Color> userColors = new HashMap<>();

	public ChatClient(String host, int port) {
		try {
			socket = new Socket(host, port);
			writer = new PrintWriter(socket.getOutputStream(), true);
			createUI();
			authenticate();
			startListener();
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null, "Unable to connect to server", "Connection Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	// Khởi tạo giao diện ứng dụng chat
	private void createUI() {
		frame = new JFrame("Modern Chat Client");
		frame.setSize(800, 500);
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
		frame.add(new JScrollPane(userList), BorderLayout.EAST);

		JPanel inputPanel = new JPanel(new BorderLayout());
		inputField = new JTextField();
		sendButton = new JButton("Send");

		attachButton = new JButton("📎 File");
		attachButton.addActionListener(e -> sendFile());

		sendButton.setBackground(new Color(100, 149, 237));
		sendButton.setForeground(Color.WHITE);
		sendButton.setFocusPainted(false);
		sendButton.addActionListener(e -> sendMessage());
		inputField.addActionListener(e -> sendMessage());

		inputPanel.add(attachButton, BorderLayout.WEST);
		inputPanel.add(inputField, BorderLayout.CENTER);
		inputPanel.add(sendButton, BorderLayout.EAST);
		inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
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
	}

	// Xử lý chọn và gửi file lên Server dưới dạng mã hóa Base64
	private void sendFile() {
		JFileChooser fileChooser = new JFileChooser();
		int result = fileChooser.showOpenDialog(frame);
		if (result == JFileChooser.APPROVE_OPTION) {
			File selectedFile = fileChooser.getSelectedFile();
			long fileSizeMB = selectedFile.length() / (1024 * 1024);

			// Giới hạn dung lượng file gửi tối đa 50MB
			if (fileSizeMB > 50) {
				JOptionPane.showMessageDialog(frame, "Dung lượng file vượt quá 50MB!", "Lỗi",
						JOptionPane.ERROR_MESSAGE);
				return;
			}
			try {
				// Đọc toàn bộ byte của file và mã hóa sang chuỗi mã văn bản Base64
				byte[] fileBytes = Files.readAllBytes(selectedFile.toPath());
				String encodedString = Base64.getEncoder().encodeToString(fileBytes);
				// Định dạng giao thức gửi: FILE:tên_file:chuỗi_base64
				writer.println("FILE:" + selectedFile.getName() + ":" + encodedString);
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(frame, "Lỗi khi đọc file: " + ex.getMessage());
			}
		}
	}

	private void sendMessage() {
		String message = inputField.getText().trim();
		if (!message.isEmpty()) {
			writer.println(message);
			inputField.setText("");
		}
	}

	// Luồng lắng nghe dữ liệu liên tục từ phía Server truyền về
	private void startListener() {
		Thread listener = new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
				String message;
				while ((message = reader.readLine()) != null) {
					if (message.startsWith("USERLIST:")) {
						updateUserList(message.substring(9));
					} else if (message.startsWith("FILE:")) {

						String[] parts = message.split(":", 4);
						if (parts.length == 4) {
							String sender = parts[1];
							String fileName = parts[2];
							String base64Data = parts[3];
							try {

								byte[] decodedBytes = Base64.getDecoder().decode(base64Data);
								File downloadDir = new File("Downloads_ChatApp");
								if (!downloadDir.exists())
									downloadDir.mkdir();

								File outputFile = new File(downloadDir, fileName);
								Files.write(outputFile.toPath(), decodedBytes);

								addFileBubble(sender, outputFile);
							} catch (Exception ex) {
								System.out.println("Lỗi tải file: " + ex.getMessage());
							}
						}
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

	// Tạo bong bóng chat
	private void addFileBubble(String sender, File file) {
		SwingUtilities.invokeLater(() -> {
			JPanel bubblePanel = new JPanel();
			bubblePanel.setLayout(new FlowLayout(username.equals(sender) ? FlowLayout.RIGHT : FlowLayout.LEFT));
			bubblePanel.setOpaque(false);

			JPanel contentPanel = new JPanel();
			contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
			contentPanel.setBackground(userColors.computeIfAbsent(sender, k -> generateColor(k)));
			contentPanel.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(15),
					BorderFactory.createEmptyBorder(5, 10, 5, 10)));
			contentPanel.setOpaque(true);

			JLabel nameLabel = new JLabel(sender + ":");
			nameLabel.setForeground(Color.WHITE);
			nameLabel.setFont(new Font("Arial", Font.BOLD, 12));
			nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			contentPanel.add(nameLabel);
			contentPanel.add(Box.createRigidArea(new Dimension(0, 5)));

			String fileName = file.getName().toLowerCase();
			// Kiểm tra phần mở rộng nếu file nhận được là hình ảnh
			if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")
					|| fileName.endsWith(".gif")) {
				try {

					BufferedImage bimg = ImageIO.read(file);
					if (bimg != null) {
						int width = bimg.getWidth();
						int height = bimg.getHeight();

						int MAX_WIDTH = 400;
						if (width > MAX_WIDTH) {
							height = (height * MAX_WIDTH) / width;
							width = MAX_WIDTH;
						}

						Image scaledImg = bimg.getScaledInstance(width, height, Image.SCALE_SMOOTH);
						JLabel imageLabel = new JLabel(new ImageIcon(scaledImg));
						imageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
						contentPanel.add(imageLabel);
					} else {
						contentPanel.add(new JLabel("[Lỗi định dạng ảnh]"));
					}
				} catch (Exception e) {
					contentPanel.add(new JLabel("[Lỗi không thể tải ảnh]"));
				}
			} else {
				// Nếu là tệp tin khác (Nhạc, video, tài liệu văn bản...) -> Tạo nút bấm mở
				// nhanh
				JButton openButton = new JButton("▶ Mở: " + file.getName());
				openButton.setAlignmentX(Component.LEFT_ALIGNMENT);
				openButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
				openButton.setBackground(Color.WHITE);
				openButton.setFocusPainted(false);

				// Gọi Desktop API để kích hoạt phần mềm mặc định của máy tính xử lý tệp tin
				// tương ứng
				openButton.addActionListener(e -> {
					try {
						if (Desktop.isDesktopSupported()) {
							Desktop.getDesktop().open(file);
						}
					} catch (IOException ex) {
						JOptionPane.showMessageDialog(frame,
								"Không tìm thấy phần mềm phù hợp trên máy tính để chạy tệp này!");
					}
				});
				contentPanel.add(openButton);
			}

			bubblePanel.add(contentPanel);
			chatPanel.add(bubblePanel);
			chatPanel.revalidate();
			chatScroll.getVerticalScrollBar().setValue(chatScroll.getVerticalScrollBar().getMaximum());
		});
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
			bubblePanel.setLayout(
					new FlowLayout(username.equals(getSender(message)) ? FlowLayout.RIGHT : FlowLayout.LEFT));
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

		RoundedBorder(int r) {
			radius = r;
		}

		public Insets getBorderInsets(Component c) {
			return new Insets(radius + 1, radius + 1, radius + 2, radius);
		}

		public boolean isBorderOpaque() {
			return false;
		}

		public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
			g.setColor(Color.BLACK);
			g.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
		}
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

			String[] options = new String[] { "Đăng nhập", "Đăng ký", "Thoát" };
			int option = JOptionPane.showOptionDialog(frame, panel, "Xác thực tài khoản", JOptionPane.DEFAULT_OPTION,
					JOptionPane.PLAIN_MESSAGE, null, options, options[0]);

			if (option == 2 || option == JOptionPane.CLOSED_OPTION) {
				System.exit(0);
			}

			String user = userField.getText().trim();
			String pass = new String(passField.getPassword()).trim();
			String command = (option == 0) ? "LOGIN:" : "REG:";

			writer.println(command + user + ":" + pass);

			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				String response = reader.readLine();

				if (response != null && response.equals("LOGIN_SUCCESS")) {
					this.username = user;
					statusLabel.setText("Connected as: " + username);
					JOptionPane.showMessageDialog(frame, "Đăng nhập thành công!");
					break;

				} else if (response != null && response.equals("REG_SUCCESS")) {
					JOptionPane.showMessageDialog(frame, "Đăng ký thành công! Vui lòng đăng nhập lại.");

				} else if (response != null) {
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
}