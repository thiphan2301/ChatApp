import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
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
	private JButton attachButton; // Nút gửi file
	private JButton settingsButton; // Nút dành riêng điều khiển cấu hình phòng cho Host
	private JLabel statusLabel;
	private DefaultListModel<String> userListModel;
	private JList<String> userList;
	private Map<String, Color> userColors = new HashMap<>();
	private List<String> availableRooms = new ArrayList<>();
	
	// Các trường phục vụ chức năng Trả lời, Cảm xúc, Thu hồi từ 0719575
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
			serverReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			createUI();
			authenticate();
			startListener();

		} catch (IOException e) {
			JOptionPane.showMessageDialog(null, "Unable to connect to server", "Connection Error",
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
		chatScroll.getVerticalScrollBar().setUnitIncrement(16); // Cuộn mượt hơn
		frame.add(chatScroll, BorderLayout.CENTER);

		userListModel = new DefaultListModel<>();
		userList = new JList<>(userListModel);
		userList.setPreferredSize(new Dimension(150, 0));

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

		JPanel bottomPanel = new JPanel(new BorderLayout());

		// Khung UI Preview cho chức năng Trả lời (Reply)
		replyPreviewPanel = new JPanel(new BorderLayout());
		replyPreviewPanel.setBackground(new Color(220, 220, 220));
		replyPreviewPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
		replyPreviewPanel.setVisible(false);
		replyPreviewLabel = new JLabel();
		
		// UC 11 - Alternative Flow A2: Người dùng hủy thao tác trả lời
		JButton cancelReplyBtn = new JButton("X");
		cancelReplyBtn.setMargin(new Insets(0, 5, 0, 5));
		cancelReplyBtn.addActionListener(e -> {
			// //11.A2.1. Người dùng nhấn nút hủy bỏ thao tác trên khung preview trả lời
			clearReplyState();
		});
		replyPreviewPanel.add(new JLabel("↪ Đang trả lời: "), BorderLayout.WEST);
		replyPreviewPanel.add(replyPreviewLabel, BorderLayout.CENTER);
		replyPreviewPanel.add(cancelReplyBtn, BorderLayout.EAST);

		JPanel inputPanel = new JPanel(new BorderLayout());
		inputField = new JTextField();

		// UC 8 - Bước 8.1: Người dùng click vào biểu tượng gửi file đính kèm
		attachButton = new JButton("📎 File");
		attachButton.addActionListener(e -> {
			// //8.1.1. Người dùng kích hoạt sự kiện bấm nút đính kèm file tệp tin trên giao diện client
			sendFile();
		});

		JPanel buttonPanel = new JPanel(new GridLayout(1, 3, 5, 0));
		JButton roomButton = new JButton("Rooms");
		roomButton.setBackground(new Color(60, 179, 113));
		roomButton.setForeground(Color.WHITE);
		roomButton.setFocusPainted(false);
		roomButton.addActionListener(e -> {
			RoomSelectionDialog dialog = new RoomSelectionDialog(frame, availableRooms);
			dialog.setVisible(true);

			if (dialog.getActionType() != null) {
				if (dialog.getActionType().equals("CREATE")) {
					writer.println("/create " + dialog.getRoomName() + "|" + dialog.getRoomKey() + "|"
							+ dialog.isAllowHistory() + "|" + dialog.isAllowNewJoins());
				} else if (dialog.getActionType().equals("JOIN")) {
					writer.println("/join " + dialog.getRoomName() + "|" + dialog.getRoomKey());
				}
			}
		});

		settingsButton = new JButton("Settings");
		settingsButton.setEnabled(false);
		settingsButton.setBackground(new Color(218, 165, 32));
		settingsButton.setForeground(Color.WHITE);
		settingsButton.setFocusPainted(false);
		settingsButton.addActionListener(e -> {
			JCheckBox chkHist = new JCheckBox("Cho phép người mới xem lịch sử chat trước đó", true);
			JCheckBox chkJoin = new JCheckBox("Cho phép người mới tham gia vào phòng", true);
			Object[] optionMessage = { chkHist, chkJoin };
			int option = JOptionPane.showConfirmDialog(frame, optionMessage, "Cài đặt phòng (Chủ phòng)",
					JOptionPane.OK_CANCEL_OPTION);
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

		inputPanel.add(attachButton, BorderLayout.WEST);
		inputPanel.add(inputField, BorderLayout.CENTER);
		inputPanel.add(buttonPanel, BorderLayout.EAST);
		inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		bottomPanel.add(replyPreviewPanel, BorderLayout.NORTH);
		bottomPanel.add(inputPanel, BorderLayout.CENTER);

		frame.add(bottomPanel, BorderLayout.SOUTH);

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

	private void clearReplyState() {
		replyingToText = null;
		replyPreviewPanel.setVisible(false);
		frame.revalidate();
	}

	// UC 8: Xử lý chọn và gửi file lên hệ thống máy chủ
	private void sendFile() {
		// //8.2.1. Hệ thống khởi tạo và mở cửa sổ JFileChooser để người dùng lựa chọn file cục bộ
		JFileChooser fileChooser = new JFileChooser();
		int result = fileChooser.showOpenDialog(frame);
		
		if (result == JFileChooser.APPROVE_OPTION) {
			// //8.3.1. Người dùng chọn một file từ hệ thống lưu trữ của thiết bị cá nhân
			File selectedFile = fileChooser.getSelectedFile();
			
			// //8.4.1. Hệ thống thực hiện kiểm tra dung lượng của tệp tin trước khi tải lên (Giới hạn tối đa 50MB)
			long fileSizeMB = selectedFile.length() / (1024 * 1024);
			
			// UC 8 - Alternative Flow A1: File được chọn có dung lượng vượt quá giới hạn cho phép
			if (fileSizeMB > 50) {
				// //8.A1.1. Hệ thống thông báo lỗi dung lượng và từ chối xử lý tải file lên máy chủ công cộng
				JOptionPane.showMessageDialog(frame, "Dung lượng file vượt quá 50MB!", "Lỗi hệ thống",
						JOptionPane.ERROR_MESSAGE);
				return;
			}
			
			try {
				// //8.5.1. Người dùng nhấn nút xác nhận truyền file qua mạng phòng chat
				byte[] fileBytes = Files.readAllBytes(selectedFile.toPath());
				String encodedString = Base64.getEncoder().encodeToString(fileBytes);
				String uuid = UUID.randomUUID().toString();
				
				// //8.6.1. Client đóng gói dữ liệu chuỗi Base64 và thực hiện gửi gói lệnh dạng FILE truyền tới Server
				writer.println("FILE:" + uuid + ":" + selectedFile.getName() + ":" + encodedString);
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(frame, "Đã xảy ra lỗi khi đọc tệp tin: " + ex.getMessage());
			}
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

			if (user.isEmpty() || pass.isEmpty()) {
				JOptionPane.showMessageDialog(frame, "Vui lòng nhập đầy đủ thông tin!", "Cảnh báo",
						JOptionPane.WARNING_MESSAGE);
				continue;
			}

			String command = (option == 0) ? "LOGIN:" : "REG:";
			writer.println(command + user + ":" + pass);

			try {
				String response = serverReader.readLine();
				if (response != null && response.equals("LOGIN_SUCCESS")) {
					this.username = user;
					statusLabel.setText("Connected as: " + username);
					JOptionPane.showMessageDialog(frame, "Đăng nhập thành công!");
					break; 
				} else if (response != null && response.equals("REG_SUCCESS")) {
					JOptionPane.showMessageDialog(frame, "Đăng ký thành công! Vui lòng thực hiện đăng nhập.");
				} else if (response != null) {
					String[] errorParts = response.split(":", 2);
					String errorMsg = (errorParts.length > 1) ? errorParts[1] : "Lỗi không xác định";
					JOptionPane.showMessageDialog(frame, errorMsg, "Thất bại", JOptionPane.ERROR_MESSAGE);
				}
			} catch (IOException ex) {
				JOptionPane.showMessageDialog(frame, "Mất kết nối tới máy chủ xác thực!");
				System.exit(0);
			}
		}
	}

	private void sendMessage() {
		String message = inputField.getText().trim();
		if (message.isEmpty()) {
			return;
		}

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
		} else if (message.startsWith("/create ") || message.startsWith("/join ") || message.startsWith("/settings ")) {
			writer.println(message);
		} else {
			String uuid = UUID.randomUUID().toString();
			
			// UC 11 - Gửi nội dung tin nhắn Trả lời phản hồi (Luồng 9.3.B3 và 9.3.B4)
			if (replyingToText != null) {
				// //11.9.3.B3.1. Người dùng gõ nội dung vào ô nhập liệu chính và nhấn phím Enter gửi đi
				// //11.9.3.B4.1. Client cấu trúc và bắn chuỗi dữ liệu chứa giao thức REPLY kèm nội dung trích dẫn lên máy chủ
				writer.println("REPLY:" + uuid + ":" + replyingToText + "|" + message);
				clearReplyState();
			} else {
				writer.println("CHAT:" + uuid + ":" + message);
			}
		}
		inputField.setText("");
	}

	private void startListener() {
		Thread listener = new Thread(() -> {
			try {
				String message;
				while ((message = serverReader.readLine()) != null) {
					if (message.startsWith("USERLIST:")) {
						updateUserList(message.substring(9));
					} else if (message.startsWith("ROOMLIST:")) {
						String[] rooms = message.substring(9).split(",");
						availableRooms = Arrays.asList(rooms);
					} else if (message.startsWith("ROOM_CHANGED:")) {
						String currentRoom = message.substring(13);
						SwingUtilities.invokeLater(() -> {
							statusLabel.setText("Connected as: " + username + " | Room: " + currentRoom);
							chatPanel.removeAll();
							chatPanel.revalidate();
							chatPanel.repaint();
						});
					} else if (message.startsWith("IS_HOST:")) {
						boolean isHost = Boolean.parseBoolean(message.substring(8));
						SwingUtilities.invokeLater(() -> {
							settingsButton.setEnabled(isHost); 
						});
					} else if (message.startsWith("ROOM_ERROR:")) {
						String errorMsg = message.substring(11);
						SwingUtilities.invokeLater(() -> {
							JOptionPane.showMessageDialog(frame, errorMsg, "Lỗi phòng", JOptionPane.ERROR_MESSAGE);
						});
					} 
					// UC 8 - Bước 8.6 & 8.7: Tiếp nhận đồng bộ tệp tin đa phương tiện gửi từ Server
					else if (message.startsWith("FILE:")) {
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
								
								// //8.6.2. Hệ thống tải dữ liệu xuống thư mục Downloads nội bộ của Client một cách tự động
								Files.write(outputFile.toPath(), decodedBytes);
								
								// //8.7.1. Cập nhật và hiển thị trực quan tệp tin đính kèm (Hình ảnh/Nút bấm) lên màn hình phòng chat công cộng
								addBubble(uuid, sender, null, null, outputFile);
							} catch (Exception ex) {
								System.out.println("Lỗi đồng bộ tệp đính kèm: " + ex.getMessage());
							}
						}
					} else if (message.startsWith("CHAT:")) {
						String[] p = message.split(":", 4);
						if (p.length == 4)
							addBubble(p[1], p[2], p[3], null, null);
					} 
					// UC 11 - Bước 9.3.B4: Tiếp nhận và hiển thị một tin nhắn trích dẫn (REPLY) được trả về từ phía Server
					else if (message.startsWith("REPLY:")) {
						String[] p = message.split(":", 4);
						if (p.length == 4) {
							String[] replyParts = p[3].split("\\|", 2);
							String quote = replyParts[0];
							String content = replyParts.length > 1 ? replyParts[1] : "";
							
							// //11.9.3.B4.3. Giao diện client dựng bong bóng phản hồi kèm ô trích dẫn nội dung gốc phía trên văn bản mới
							addBubble(p[1], p[2], content, quote, null);
						}
					} 
					// UC 11 - Bước 9.3.C4: Tiếp nhận thông tin thu hồi gói tin từ máy chủ phát tới
					else if (message.startsWith("RECALL:")) {
						// //11.9.3.C4.2. Khớp mã định danh tin nhắn và tiến hành xóa bỏ thông tin hiển thị tại client cục bộ
						handleRecallMessage(message.split(":")[1]);
					} 
					// UC 11 - Bước 9.3.A2: Nhận gói dữ liệu cập nhật trạng thái cảm xúc từ máy chủ
					else if (message.startsWith("REACT:")) {
						String[] p = message.split(":", 4);
						if (p.length == 4)
							// //11.9.3.A2.2. Nhận tín hiệu biểu tượng cảm xúc đồng bộ và re-render cấu trúc danh sách icon biểu cảm dưới bong bóng chat
							handleReaction(p[1], p[2], p[3]);
					} else if (message.startsWith("SYSTEM:")) {
						addSystemMessage(message);
					} else if (!message.contains(":")) {
						addSystemMessage(message);
					} else {
						addMessageBubble(message);
					}
				}
			} catch (IOException e) {
				addSystemMessage("Mất kết nối tới Server.");
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

			JLabel nameLabel = new JLabel(sender + ":");
			nameLabel.setForeground(Color.WHITE);
			nameLabel.setFont(new Font("Arial", Font.BOLD, 12));
			nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			contentPanel.add(nameLabel);
			contentPanel.add(Box.createRigidArea(new Dimension(0, 5)));

			// Dựng cấu trúc giao diện trích dẫn câu hỏi cũ (Quote UI Block) của UC 11
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

			// Giao diện hiển thị đính kèm tệp tin đa phương tiện thuộc luồng UC 8
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
							mainContent = new JLabel("[Lỗi tệp hình ảnh không hợp lệ]");
						}
					} catch (Exception e) {
						mainContent = new JLabel("[Lỗi phân tách hình ảnh hiển thị]");
					}
				} else {
					JButton openButton = new JButton("▶ Click Mở File: " + file.getName());
					openButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
					openButton.setBackground(Color.WHITE);
					openButton.setFocusPainted(false);
					openButton.addActionListener(e -> {
						try {
							if (Desktop.isDesktopSupported())
								Desktop.getDesktop().open(file);
						} catch (IOException ex) {
							JOptionPane.showMessageDialog(frame, "Không thể mở ứng dụng xem file mặc định cục bộ.");
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

			JPanel mainContentWrapper = new JPanel(new BorderLayout());
			mainContentWrapper.setOpaque(false);
			mainContentWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
			mainContentWrapper.add(mainContent, BorderLayout.CENTER);
			contentPanel.add(mainContentWrapper);

			JPanel reactionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
			reactionPanel.setOpaque(false);
			reactionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			contentPanel.add(reactionPanel);

			contentWrapperMap.put(uuid, mainContentWrapper);
			reactionPanelMap.put(uuid, reactionPanel);

			// UC 11 - Bước 9.1 và 9.2: Đăng ký lắng nghe sự kiện nhấn chuột phải bật menu tương tác tin nhắn
			JPopupMenu popupMenu = createInteractionMenu(uuid, sender, rawTextForMenu);
			mainContent.addMouseListener(new MouseAdapter() {
				public void mousePressed(MouseEvent e) {
					showPopup(e);
				}

				public void mouseReleased(MouseEvent e) {
					showPopup(e);
				}

				private void showPopup(MouseEvent e) {
					// //11.9.1.1. Người dùng click nút chuột phải lên vùng text tin nhắn chat cụ thể
					if (e.isPopupTrigger() && mainContentWrapper.getComponentCount() > 0) {
						Component comp = mainContentWrapper.getComponent(0);
						boolean isRecalled = (comp instanceof JLabel)
								&& "Tin nhắn đã bị thu hồi".equals(((JLabel) comp).getText());
						if (!isRecalled) {
							// //11.9.2.1. Hệ thống dựng một menu pop-up hiển thị danh mục lệnh: Thả cảm xúc, Trả lời, Thu hồi
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

	// UC 11 - Bước 9.3.C4: Cập nhật giao diện xóa văn bản gốc khi tin nhắn bị thu hồi thành công
	private void handleRecallMessage(String uuid) {
		SwingUtilities.invokeLater(() -> {
			JPanel wrapper = contentWrapperMap.get(uuid);
			if (wrapper != null) {
				wrapper.removeAll();
				// //11.9.3.C4.3. Hệ thống xóa trắng vùng hiển thị text cũ và ghi đè trạng thái "Tin nhắn đã bị thu hồi" dạng chữ nghiêng màu xám
				JLabel recalledLabel = new JLabel("Tin nhắn đã bị thu hồi");
				recalledLabel.setFont(new Font("Arial", Font.ITALIC, 13));
				recalledLabel.setForeground(Color.LIGHT_GRAY);
				wrapper.add(recalledLabel, BorderLayout.CENTER);
				wrapper.revalidate();
				wrapper.repaint();
				
				// UC 11 - Luồng phụ A1 (Xóa cảm xúc): Tự động gỡ bỏ các icon biểu cảm thuộc dòng tin nhắn bị xóa
				JPanel reactPanel = reactionPanelMap.get(uuid);
				if (reactPanel != null) {
					reactPanel.removeAll();
					reactPanel.revalidate();
					reactPanel.repaint();
				}
			}
		});
	}

	// UC 11 - Bước 9.3.A2: Kết xuất và vẽ lại icon biểu tượng thả cảm xúc lên bong bóng chat
	private void handleReaction(String uuid, String sender, String emoji) {
		SwingUtilities.invokeLater(() -> {
			JPanel reactPanel = reactionPanelMap.get(uuid);
			if (reactPanel == null)
				return;
			List<String> reactions = reactionDataMap.computeIfAbsent(uuid, k -> new ArrayList<>());
			String reactKey = sender + "|" + emoji;
			
			// UC 11 - Alternative Flow A1: Người dùng nhấn lại cùng một loại emoji để xóa bỏ cảm xúc cũ
			if (reactions.contains(reactKey)) {
				// //11.A1.1. Hệ thống xóa bản ghi biểu cảm ra khỏi danh sách lưu trữ của Client cục bộ
				reactions.remove(reactKey); 
			} else {
				reactions.add(reactKey); 
			}
			
			reactPanel.removeAll();
			for (String r : reactions) {
				String emj = r.split("\\|")[1];
				JLabel emojiLabel = new JLabel(emj);
				emojiLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
				
				// //11.9.3.A2.3. Hệ thống tạo các nhãn JLabel chứa emoji đính kèm vào phần chân của bong bóng tin nhắn chat nhóm
				reactPanel.add(emojiLabel);
			}
			reactPanel.revalidate();
			reactPanel.repaint();
		});
	}

	// UC 11 - Bước 9.3: Thiết kế tạo lập Menu ngữ cảnh click chuột phải và liên kết xử lý lệnh tương tác
	private JPopupMenu createInteractionMenu(String uuid, String sender, String rawText) {
		JPopupMenu menu = new JPopupMenu();
		
		// Luồng 9.3.A1: Người dùng lựa chọn chức năng Thả cảm xúc biểu cảm
		JMenu reactMenu = new JMenu("Thả cảm xúc");
		String[] emojis = { "👍", "❤️", "😂", "😮", "😢", "😡" };
		for (String emj : emojis) {
			JMenuItem item = new JMenuItem(emj);
			item.addActionListener(e -> {
				// //11.9.3.A1.1. Người dùng click chọn một biểu tượng cụ thể từ danh sách icon hiển thị trong menu con đổ xuống
				writer.println("REACT:" + uuid + ":" + emj);
			});
			reactMenu.add(item);
		}
		menu.add(reactMenu);
		
		// Luồng 9.3.B1 & 9.3.B2: Người dùng chọn tính năng Trả lời (Reply)
		JMenuItem replyItem = new JMenuItem("Trả lời");
		replyItem.addActionListener(e -> {
			// //11.9.3.B1.1. Người dùng bấm chuột trái lựa chọn chức năng "Trả lời" trên thanh Menu ngữ cảnh
			replyingToText = sender + ": " + rawText;
			replyPreviewLabel.setText(replyingToText);
			
			// //11.9.3.B2.1. Hệ thống hiển thị cấu trúc thanh Preview Panel chứa nội dung tóm lược dòng chat cũ ở trên khu vực input
			replyPreviewPanel.setVisible(true);
			inputField.requestFocus();
			frame.revalidate();
		});
		menu.add(replyItem);
		
		// Luồng 9.3.C1 & 9.3.C2: Người dùng lựa chọn chức năng Thu Hồi (Chỉ hỗ trợ hiển thị với tin nhắn do chính mình gửi đi)
		if (username.equals(sender)) {
			menu.addSeparator();
			JMenuItem recallItem = new JMenuItem("Thu hồi tin nhắn");
			recallItem.addActionListener(e -> {
				// //11.9.3.C1.1. Người dùng bấm chọn dòng chữ tính năng lệnh "Thu hồi tin nhắn"
				
				// //11.9.3.C2.1. Hệ thống kích hoạt hiển thị hộp thoại xác nhận lựa chọn hành động từ người dùng: JConfirmDialog
				int confirm = JOptionPane.showConfirmDialog(frame, "Bạn có chắc chắn muốn thu hồi tin nhắn này không?", "Xác nhận hành động",
						JOptionPane.YES_NO_OPTION);
						
				// //11.9.3.C3.1. Người dùng click chuột xác nhận đồng ý nhấn nút "Yes" (Đồng ý)
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
}