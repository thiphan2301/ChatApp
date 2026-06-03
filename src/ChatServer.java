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
					if (message.startsWith("FILE:")) {
						// FILE:uuid:fileName:base64
						String[] parts = message.split(":", 4);
						if (parts.length == 4) {
							String uuid = parts[1];
							String fileName = parts[2];
							String base64Data = parts[3];
							DatabaseManager.saveMessage(uuid, username, "[Đính kèm file]: " + fileName);
							broadcast("FILE:" + uuid + ":" + username + ":" + fileName + ":" + base64Data);
						}
					} else if (message.startsWith("CHAT:")) {
						// CHAT:uuid:text
						String[] parts = message.split(":", 3);
						if(parts.length == 3) {
							DatabaseManager.saveMessage(parts[1], username, parts[2]);
							broadcast("CHAT:" + parts[1] + ":" + username + ":" + parts[2]);
						}
					} else if (message.startsWith("REPLY:")) {
						// REPLY:uuid:quote|text
						String[] parts = message.split(":", 3);
						if(parts.length == 3) {
							DatabaseManager.saveMessage(parts[1], username, "[Trả lời]: " + parts[2]);
							broadcast("REPLY:" + parts[1] + ":" + username + ":" + parts[2]);
						}
					} else if (message.startsWith("RECALL:")) {
						// RECALL:uuid
						String uuid = message.split(":")[1];
						DatabaseManager.recallMessage(uuid);
						broadcast("RECALL:" + uuid);
					} else if (message.startsWith("REACT:")) {
						// REACT:uuid:emoji
						String[] parts = message.split(":", 3);
						if(parts.length == 3) {
							broadcast("REACT:" + parts[1] + ":" + username + ":" + parts[2]);
						}
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