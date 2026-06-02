import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
	private ServerSocket serverSocket;
	private List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());

	public ChatServer(int port) {
		try {
			serverSocket = new ServerSocket(port);
			System.out.println("Server started on port " + port);
		} catch (IOException e) {
			System.out.println("Error starting server: " + e.getMessage());
		}
	}

	public void start() {
		try {
			while (true) {
				Socket clientSocket = serverSocket.accept();
				ClientHandler client = new ClientHandler(clientSocket);
				clients.add(client);
				client.start();
			}
		} catch (IOException e) {
			System.out.println("Error accepting client: " + e.getMessage());
		}
	}

	public void broadcast(String message) {
		synchronized (clients) {
			for (ClientHandler client : clients) {
				client.sendMessage(message);
			}
		}
	}

	public void updateUserList() {
		synchronized (clients) {
			StringBuilder sb = new StringBuilder();
			sb.append("USERLIST:");
			for (ClientHandler client : clients) {
				sb.append(client.getUsername()).append(",");
			}
			String listMessage = sb.toString();
			for (ClientHandler client : clients) {
				client.sendMessage(listMessage);
			}
		}
	}

	public void removeClient(ClientHandler client) {
		clients.remove(client);
		broadcast(client.getUsername() + " has left the chat.");
		updateUserList();
	}

	public static void main(String[] args) {
		ChatServer server = new ChatServer(8000);
		server.start();
	}

	class ClientHandler extends Thread {
		private Socket socket;
		private PrintWriter writer;
		private String username;

		public ClientHandler(Socket socket) {
			this.socket = socket;
		}

		public String getUsername() {
			return username;
		}

		public void run() {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
				writer = new PrintWriter(socket.getOutputStream(), true);

				boolean loggedIn = false;

				while (!loggedIn) {
					String authRequest = reader.readLine();
					if (authRequest == null)
						return;

					String[] tokens = authRequest.split(":", -1);
					if (tokens.length < 3)
						continue;

					String command = tokens[0];
					String user = tokens[1];
					String pass = tokens[2];

					if ("REG".equals(command)) {
						if (DatabaseManager.registerUser(user, pass)) {
							writer.println("REG_SUCCESS");
						} else {
							writer.println("REG_FAIL:Tên tài khoản đã tồn tại hoặc lỗi DB!");
						}
					} else if ("LOGIN".equals(command)) {
						if (DatabaseManager.verifyUser(user, pass)) {
							this.username = user;
							writer.println("LOGIN_SUCCESS");
							loggedIn = true;

							broadcast(username + " has joined the chat!");
							updateUserList();
						} else {
							writer.println("LOGIN_FAIL:Sai tài khoản hoặc mật khẩu.");
						}
					}
				}

				String message;
				while ((message = reader.readLine()) != null) {
					// Kiểm tra gói dữ liệu truyền tải thông tin file
					if (message.startsWith("FILE:")) {
						// Tách chuỗi thành tối đa 3 phần (FILE, tên file, dữ liệu mã hoá Base64)
						String[] parts = message.split(":", 3);
						if (parts.length == 3) {
							String fileName = parts[1];
							String base64Data = parts[2];

							// Lưu vết hoạt động đính kèm file vào Database lịch sử chat
							DatabaseManager.saveMessage(username, "[Đính kèm file]: " + fileName);

							// Gửi phân phối gói tin file đến toàn bộ người dùng đang online trong phòng
							broadcast("FILE:" + username + ":" + fileName + ":" + base64Data);
							System.out.println(username + " vừa gửi 1 file: " + fileName);
						}
					} else {
						// Lưu và phát sóng gói tin nhắn văn bản thông thường
						DatabaseManager.saveMessage(username, message);
						broadcast(username + ":" + message);
						System.out.println(username + ": " + message);
					}
				}

			} catch (IOException e) {
				System.out.println(username + " disconnected.");
			} finally {
				if (username != null) {
					removeClient(this);
				}
				try {
					socket.close();
				} catch (IOException ignored) {
				}
			}
		}

		public void sendMessage(String message) {
			if (writer != null) {
				writer.println(message);
			}
		}
	}
}