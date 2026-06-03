import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
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
	private JPanel replyPreviewPanel;
	private JLabel replyPreviewLabel;
	private String replyingToText = null;
	private Map<String, JPanel> contentWrapperMap = new HashMap<>();
	private Map<String, JPanel> reactionPanelMap = new HashMap<>();
	private Map<String, List<String>> reactionDataMap = new HashMap<>(); 
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
		chatScroll.getVerticalScrollBar().setUnitIncrement(16);
		frame.add(chatScroll, BorderLayout.CENTER);

		userListModel = new DefaultListModel<>();
		userList = new JList<>(userListModel);
		userList.setPreferredSize(new Dimension(150, 0));
		frame.add(new JScrollPane(userList), BorderLayout.EAST);

		JPanel bottomPanel = new JPanel(new BorderLayout());
		replyPreviewPanel = new JPanel(new BorderLayout());
		replyPreviewPanel.setBackground(new Color(220, 220, 220));
		replyPreviewPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
		replyPreviewPanel.setVisible(false);
		replyPreviewLabel = new JLabel();
		JButton cancelReplyBtn = new JButton("X");
		cancelReplyBtn.setMargin(new Insets(0, 5, 0, 5));
		cancelReplyBtn.addActionListener(e -> clearReplyState());
		replyPreviewPanel.add(new JLabel("↪ Đang trả lời: "), BorderLayout.WEST);
		replyPreviewPanel.add(replyPreviewLabel, BorderLayout.CENTER);
		replyPreviewPanel.add(cancelReplyBtn, BorderLayout.EAST);

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

		bottomPanel.add(replyPreviewPanel, BorderLayout.NORTH);
		bottomPanel.add(inputPanel, BorderLayout.CENTER);
		frame.add(bottomPanel, BorderLayout.SOUTH);

		JMenuBar menuBar = new JMenuBar();
		JMenu menu = new JMenu("Options");
		JMenuItem clearItem = new JMenuItem("Clear Chat");
		clearItem.addActionListener(e -> {
			chatPanel.removeAll();
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

	private void clearReplyState() {
		replyingToText = null;
		replyPreviewPanel.setVisible(false);
		frame.revalidate();
	}

	private void sendFile() {
		JFileChooser fileChooser = new JFileChooser();
		int result = fileChooser.showOpenDialog(frame);
		if (result == JFileChooser.APPROVE_OPTION) {
			File selectedFile = fileChooser.getSelectedFile();
			long fileSizeMB = selectedFile.length() / (1024 * 1024);
			if (fileSizeMB > 50) {
				JOptionPane.showMessageDialog(frame, "Dung lượng file vượt quá 50MB!", "Lỗi",
						JOptionPane.ERROR_MESSAGE);
				return;
			}
			try {
				byte[] fileBytes = Files.readAllBytes(selectedFile.toPath());
				String encodedString = Base64.getEncoder().encodeToString(fileBytes);
				String uuid = UUID.randomUUID().toString();
				writer.println("FILE:" + uuid + ":" + selectedFile.getName() + ":" + encodedString);
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(frame, "Lỗi khi đọc file: " + ex.getMessage());
			}
		}
	}

	private void sendMessage() {
		String message = inputField.getText().trim();
		if (!message.isEmpty()) {
			String uuid = UUID.randomUUID().toString();
			if (replyingToText != null) {
				writer.println("REPLY:" + uuid + ":" + replyingToText + "|" + message);
				clearReplyState();
			} else {
				writer.println("CHAT:" + uuid + ":" + message);
			}
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
					} else if (message.startsWith("FILE:")) {
						String[] parts = message.split(":", 5);
						if (parts.length == 5) {
							String uuid = parts[1];
							String sender = parts[2];
							String fileName = parts[3];
							String base64Data = parts[4];
							try {
								byte[] decodedBytes = Base64.getDecoder().decode(base64Data);
								File downloadDir = new File("Downloads_ChatApp");
								if (!downloadDir.exists())
									downloadDir.mkdir();
								File outputFile = new File(downloadDir, fileName);
								Files.write(outputFile.toPath(), decodedBytes);
								addBubble(uuid, sender, null, null, outputFile);
							} catch (Exception ex) {
								System.out.println("Lỗi tải file: " + ex.getMessage());
							}
						}
					} else if (message.startsWith("CHAT:")) {
						String[] p = message.split(":", 4);
						if (p.length == 4)
							addBubble(p[1], p[2], p[3], null, null);
					} else if (message.startsWith("REPLY:")) {
						String[] p = message.split(":", 4);
						if (p.length == 4) {
							String[] replyParts = p[3].split("\\|", 2);
							String quote = replyParts[0];
							String content = replyParts.length > 1 ? replyParts[1] : "";
							addBubble(p[1], p[2], content, quote, null);
						}
					} else if (message.startsWith("RECALL:")) {
						handleRecallMessage(message.split(":")[1]);
					} else if (message.startsWith("REACT:")) {
						String[] p = message.split(":", 4);
						if (p.length == 4)
							handleReaction(p[1], p[2], p[3]);
					} else if (!message.contains(":")) {
						
						addSystemMessage(message);
					}
				}
			} catch (IOException e) {
				statusLabel.setText("Disconnected");
				inputField.setEnabled(false);
				sendButton.setEnabled(false);
			}
		});
		listener.start();
	}

	private void addSystemMessage(String msg) {
		SwingUtilities.invokeLater(() -> {
			JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
			JLabel label = new JLabel(msg);
			label.setForeground(Color.GRAY);
			label.setFont(new Font("Arial", Font.ITALIC, 12));
			panel.add(label);
			chatPanel.add(panel);
			chatPanel.revalidate();
			scrollToBottom();
		});
	}

	private void addBubble(String uuid, String sender, String text, String quote, File file) {
		SwingUtilities.invokeLater(() -> {
			boolean isMe = username.equals(sender);
			JPanel bubbleContainer = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT));
			bubbleContainer.setOpaque(false);

			JPanel contentPanel = new JPanel();
			contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
			contentPanel.setBackground(userColors.computeIfAbsent(sender, k -> generateColor(k)));
			contentPanel.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(15),
					BorderFactory.createEmptyBorder(5, 10, 5, 10)));
			contentPanel.setOpaque(true);

			// Tên người gửi
			JLabel nameLabel = new JLabel(sender + ":");
			nameLabel.setForeground(Color.WHITE);
			nameLabel.setFont(new Font("Arial", Font.BOLD, 12));
			nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			contentPanel.add(nameLabel);
			contentPanel.add(Box.createRigidArea(new Dimension(0, 5)));

			// Khung Trích dẫn (Reply)
			if (quote != null && !quote.isEmpty()) {
				JTextArea quoteArea = new JTextArea("↪ " + quote);
				quoteArea.setFont(new Font("Arial", Font.ITALIC, 12));
				quoteArea.setLineWrap(true);
				quoteArea.setWrapStyleWord(true);
				quoteArea.setEditable(false);
				quoteArea.setOpaque(true);
				quoteArea.setBackground(new Color(255, 255, 255, 100));
				quoteArea.setBorder(BorderFactory.createCompoundBorder(
						BorderFactory.createMatteBorder(0, 3, 0, 0, Color.LIGHT_GRAY),
						BorderFactory.createEmptyBorder(2, 5, 2, 5)));
				quoteArea.setAlignmentX(Component.LEFT_ALIGNMENT);
				contentPanel.add(quoteArea);
				contentPanel.add(Box.createRigidArea(new Dimension(0, 5)));
			}

			// Nội dung chính (Văn bản hoặc File)
			JComponent mainContent = null;
			String rawTextForMenu = "";

			if (file != null) {
				rawTextForMenu = "[File đính kèm]: " + file.getName();
				String fileName = file.getName().toLowerCase();
				if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")
						|| fileName.endsWith(".gif")) {
					try {
						BufferedImage bimg = ImageIO.read(file);
						if (bimg != null) {
							int width = bimg.getWidth();
							int height = bimg.getHeight();
							int MAX_WIDTH = 300;
							if (width > MAX_WIDTH) {
								height = (height * MAX_WIDTH) / width;
								width = MAX_WIDTH;
							}
							Image scaledImg = bimg.getScaledInstance(width, height, Image.SCALE_SMOOTH);
							mainContent = new JLabel(new ImageIcon(scaledImg));
						} else {
							mainContent = new JLabel("[Lỗi định dạng ảnh]");
						}
					} catch (Exception e) {
						mainContent = new JLabel("[Lỗi không thể tải ảnh]");
					}
				} else {
					JButton openButton = new JButton("▶ Mở: " + file.getName());
					openButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
					openButton.setBackground(Color.WHITE);
					openButton.setFocusPainted(false);
					openButton.addActionListener(e -> {
						try {
							if (Desktop.isDesktopSupported())
								Desktop.getDesktop().open(file);
						} catch (IOException ex) {
						}
					});
					mainContent = openButton;
				}
			} else {
				rawTextForMenu = text;
				JTextArea textArea = new JTextArea(text);
				textArea.setLineWrap(true);
				textArea.setWrapStyleWord(true);
				textArea.setEditable(false);
				textArea.setOpaque(false);
				textArea.setForeground(Color.WHITE);
				textArea.setFont(new Font("Arial", Font.PLAIN, 14));
				mainContent = textArea;
			}

			mainContent.setAlignmentX(Component.LEFT_ALIGNMENT);

			// Wrapper bọc Content chính
			JPanel mainContentWrapper = new JPanel(new BorderLayout());
			mainContentWrapper.setOpaque(false);
			mainContentWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
			mainContentWrapper.add(mainContent, BorderLayout.CENTER);
			contentPanel.add(mainContentWrapper);

			// Vùng hiển thị Reaction
			JPanel reactionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
			reactionPanel.setOpaque(false);
			reactionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			contentPanel.add(reactionPanel);

			contentWrapperMap.put(uuid, mainContentWrapper);
			reactionPanelMap.put(uuid, reactionPanel);

			JPopupMenu popupMenu = createInteractionMenu(uuid, sender, rawTextForMenu);

			
			mainContent.addMouseListener(new MouseAdapter() {
				public void mousePressed(MouseEvent e) {
					showPopup(e);
				}

				public void mouseReleased(MouseEvent e) {
					showPopup(e);
				}

				private void showPopup(MouseEvent e) {
					if (e.isPopupTrigger() && mainContentWrapper.getComponentCount() > 0) {
						Component comp = mainContentWrapper.getComponent(0);
						boolean isRecalled = (comp instanceof JLabel)
								&& "Tin nhắn đã bị thu hồi".equals(((JLabel) comp).getText());

						if (!isRecalled) {
							popupMenu.show(e.getComponent(), e.getX(), e.getY());
						}
					}
				}
			});

			bubbleContainer.add(contentPanel);
			chatPanel.add(bubbleContainer);
			chatPanel.revalidate();
			scrollToBottom();
		});
	}

	private void handleRecallMessage(String uuid) {
		SwingUtilities.invokeLater(() -> {
			JPanel wrapper = contentWrapperMap.get(uuid);
			if (wrapper != null) {
				wrapper.removeAll();
				JLabel recalledLabel = new JLabel("Tin nhắn đã bị thu hồi");
				recalledLabel.setFont(new Font("Arial", Font.ITALIC, 13));
				recalledLabel.setForeground(Color.LIGHT_GRAY);
				wrapper.add(recalledLabel, BorderLayout.CENTER);
				wrapper.revalidate();
				wrapper.repaint();

				// Xóa luôn Reaction nếu tin bị thu hồi
				JPanel reactPanel = reactionPanelMap.get(uuid);
				if (reactPanel != null) {
					reactPanel.removeAll();
					reactPanel.revalidate();
					reactPanel.repaint();
				}
			}
		});
	}

	private void handleReaction(String uuid, String sender, String emoji) {
		SwingUtilities.invokeLater(() -> {
			JPanel reactPanel = reactionPanelMap.get(uuid);
			if (reactPanel == null)
				return;

			List<String> reactions = reactionDataMap.computeIfAbsent(uuid, k -> new ArrayList<>());
			String reactKey = sender + "|" + emoji;

			// Logic Bật/Tắt cảm xúc
			if (reactions.contains(reactKey)) {
				reactions.remove(reactKey); // Hủy cảm xúc nếu đã thả
			} else {
				reactions.add(reactKey); // Thêm cảm xúc
			}

			// Xóa panel vẽ lại từ đầu
			reactPanel.removeAll();
			for (String r : reactions) {
				String emj = r.split("\\|")[1];
				JLabel emojiLabel = new JLabel(emj);
				emojiLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
				reactPanel.add(emojiLabel);
			}

			reactPanel.revalidate();
			reactPanel.repaint();
		});
	}

	private JPopupMenu createInteractionMenu(String uuid, String sender, String rawText) {
		JPopupMenu menu = new JPopupMenu();
		JMenu reactMenu = new JMenu("Thả cảm xúc");
		String[] emojis = { "👍", "❤️", "😂", "😮", "😢", "😡" };
		for (String emj : emojis) {
			JMenuItem item = new JMenuItem(emj);
			item.addActionListener(e -> writer.println("REACT:" + uuid + ":" + emj));
			reactMenu.add(item);
		}
		menu.add(reactMenu);

		JMenuItem replyItem = new JMenuItem("Trả lời");
		replyItem.addActionListener(e -> {
			replyingToText = sender + ": " + rawText;
			replyPreviewLabel.setText(replyingToText);
			replyPreviewPanel.setVisible(true);
			inputField.requestFocus();
			frame.revalidate();
		});
		menu.add(replyItem);

		if (username.equals(sender)) {
			menu.addSeparator();
			JMenuItem recallItem = new JMenuItem("Thu hồi tin nhắn");
			recallItem.addActionListener(e -> {
				int confirm = JOptionPane.showConfirmDialog(frame, "Bạn có chắc chắn muốn thu hồi?", "Xác nhận",
						JOptionPane.YES_NO_OPTION);
				if (confirm == JOptionPane.YES_OPTION) {
					writer.println("RECALL:" + uuid);
				}
			});
			menu.add(recallItem);
		}
		return menu;
	}

	private void scrollToBottom() {
		SwingUtilities.invokeLater(() -> {
			JScrollBar vertical = chatScroll.getVerticalScrollBar();
			vertical.setValue(vertical.getMaximum());
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