import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    private ServerSocket serverSocket;
    // Quản lý danh sách các phòng chat, mặc định sẽ có phòng "Lobby"
    private Map<String, Room> rooms = new ConcurrentHashMap<>();

    public ChatServer(int port) {
        try {
            serverSocket = new ServerSocket(port);
            // Khởi tạo phòng mặc định (Không có host, không mật khẩu, cho phép lịch sử và vào tự do)
            rooms.put("Lobby", new Room("Lobby", "", null, true, true));
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
                client.start();
            }
        } catch (IOException e) {
            System.out.println("Error accepting client: " + e.getMessage());
        }
    }

    // Phát tin nhắn cho tất cả người dùng trong một phòng cụ thể
    public void broadcastToRoom(String roomName, String message) {
        Room room = rooms.get(roomName);
        if (room != null) {
            room.history.add(message);
            synchronized (room.clients) {
                for (ClientHandler client : room.clients) {
                    client.sendMessage(message);
                }
            }
        }
    }

    // Cập nhật danh sách người dùng cho một phòng cụ thể
    public void updateUserList(String roomName) {
        Room room = rooms.get(roomName);
        if (room != null) {
            synchronized (room.clients) {
                StringBuilder sb = new StringBuilder("USERLIST:");
                for (ClientHandler client : room.clients) {
                    sb.append(client.getUsername()).append(",");
                }
                String listMessage = sb.toString();
                for (ClientHandler client : room.clients) {
                    client.sendMessage(listMessage);
                }
            }
        }
    }

    // Gửi danh sách các phòng hiện có cho tất cả người dùng trên server
    public void broadcastRoomList() {
        String roomNames = String.join(",", rooms.keySet());
        String msg = "ROOMLIST:" + roomNames;
        for (Room room : rooms.values()) {
            synchronized (room.clients) {
                for (ClientHandler client : room.clients) {
                    client.sendMessage(msg);
                }
            }
        }
    }

    // Gửi tin nhắn riêng (Tìm kiếm người nhận trên toàn bộ các phòng)
    public boolean sendPrivateMessage(ClientHandler senderClient, String receiverUsername, String content) {
        boolean found = false;
        for (Room room : rooms.values()) {
            synchronized (room.clients) {
                for (ClientHandler client : room.clients) {
                    if (receiverUsername.equals(client.getUsername())) {
                        client.sendMessage("[PRIVATE] " + senderClient.getUsername() + ": " + content);
                        senderClient.sendMessage("[PRIVATE to " + receiverUsername + "] " + content);
                        found = true;
                        break;
                    }
                }
            }
            if (found) break; // Thoát vòng lặp phòng nếu đã tìm thấy
        }

        if (!found) {
            senderClient.sendMessage("SYSTEM: Người nhận '" + receiverUsername + "' hiện không online.");
        }
        return found;
    }

    public static void main(String[] args) {
        ChatServer server = new ChatServer(8000);
        server.start();
    }

    // Class quản lý thông tin của một phòng chat
    class Room {
        String name;
        String key;
        ClientHandler host;
        boolean allowHistory;
        boolean allowNewJoins;
        List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
        List<String> history = Collections.synchronizedList(new ArrayList<>());

        Room(String name, String key, ClientHandler host, boolean allowHistory, boolean allowNewJoins) {
            this.name = name;
            this.key = key;
            this.host = host;
            this.allowHistory = allowHistory;
            this.allowNewJoins = allowNewJoins;
        }
    }

    // Class xử lý từng luồng kết nối của Client
    class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter writer;
        private String username;
        private String currentRoom = "Lobby"; // Mặc định ở sảnh

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
                
                // GIAI ĐOẠN 1: XÁC THỰC (ĐĂNG KÝ / ĐĂNG NHẬP)
                while (!loggedIn) {
                    String authRequest = reader.readLine();
                    if (authRequest == null) return;

                    String[] tokens = authRequest.split(":");
                    if (tokens.length < 3) continue;

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
                            
                            // Tự động gia nhập Lobby sau khi đăng nhập thành công
                            Room lobby = rooms.get("Lobby");
                            lobby.clients.add(this);
                            writer.println("ROOM_CHANGED:" + currentRoom);
                            writer.println("IS_HOST:false");
                            
                            broadcastToRoom(currentRoom, "SYSTEM: " + username + " đã tham gia phòng!");
                            updateUserList(currentRoom);
                            writer.println("ROOMLIST:" + String.join(",", rooms.keySet()));
                        } else {
                            writer.println("LOGIN_FAIL:Sai tài khoản hoặc mật khẩu.");
                        }
                    }
                }

                // GIAI ĐOẠN 2: XỬ LÝ LỆNH VÀ NHẮN TIN
                String message;
                while ((message = reader.readLine()) != null) {
                    
                    // 1. Nhắn tin riêng tư
                    if (message.startsWith("PRIVATE:")) {
                        String[] privateParts = message.split(":", 3);
                        if (privateParts.length == 3) {
                            String receiverUsername = privateParts[1];
                            String privateContent = privateParts[2];
                            
                            boolean sent = sendPrivateMessage(this, receiverUsername, privateContent);
                            if (sent) {
                                // Chỉ lưu DB khi gửi thành công
                                DatabaseManager.savePrivateMessage(username, receiverUsername, privateContent);
                                System.out.println("[PRIVATE] " + username + " -> " + receiverUsername + ": " + privateContent);
                            } else {
                                System.out.println("[PRIVATE FAILED] " + username + " -> " + receiverUsername + ": " + privateContent);
                            }
                        } else {
                            sendMessage("SYSTEM: Sai định dạng tin nhắn riêng.");
                        }
                        continue;
                    } 
                    // 2. Tạo phòng mới
                    else if (message.startsWith("/create ")) {
                        String[] parts = message.substring(8).split("\\|");
                        if (parts.length >= 4) {
                            handleCreateRoom(parts[0].trim(), parts[1].trim(), Boolean.parseBoolean(parts[2]), Boolean.parseBoolean(parts[3]));
                        }
                        continue;
                    } 
                    // 3. Tham gia phòng khác
                    else if (message.startsWith("/join ")) {
                        String[] parts = message.substring(6).split("\\|");
                        handleJoinRoom(parts[0].trim(), parts.length > 1 ? parts[1].trim() : "");
                        continue;
                    } 
                    // 4. Chủ phòng thay đổi cài đặt
                    else if (message.startsWith("/settings ")) {
                        String[] parts = message.substring(10).split("\\|");
                        if (parts.length >= 2) {
                            handleUpdateSettings(Boolean.parseBoolean(parts[0]), Boolean.parseBoolean(parts[1]));
                        }
                        continue;
                    }

                    // Chặn các tin nhắn lỗi nhịp chứa thông tin đăng nhập lọt ra phòng chat
                    if (message.startsWith("LOGIN:") || message.startsWith("REG:")) {
                        continue;
                    }

                    // 5. Tin nhắn tương tác và chia sẻ file
                    if (message.startsWith("FILE:")) {
                        // FILE:uuid:fileName:base64
                        String[] parts = message.split(":", 4);
                        if (parts.length == 4) {
                            String uuid = parts[1];
                            String fileName = parts[2];
                            String base64Data = parts[3];
                            DatabaseManager.saveMessage(uuid, username, "[" + currentRoom + "] [Đính kèm file]: " + fileName);
                            broadcastToRoom(currentRoom, "FILE:" + uuid + ":" + username + ":" + fileName + ":" + base64Data);
                        }
                    } else if (message.startsWith("CHAT:")) {
                        // CHAT:uuid:text
                        String[] parts = message.split(":", 3);
                        if (parts.length == 3) {
                            DatabaseManager.saveMessage(parts[1], username, "[" + currentRoom + "] " + parts[2]);
                            broadcastToRoom(currentRoom, "CHAT:" + parts[1] + ":" + username + ":" + parts[2]);
                        }
                    } else if (message.startsWith("REPLY:")) {
                        // REPLY:uuid:quote|text
                        String[] parts = message.split(":", 3);
                        if (parts.length == 3) {
                            DatabaseManager.saveMessage(parts[1], username, "[" + currentRoom + "] [Trả lời]: " + parts[2]);
                            broadcastToRoom(currentRoom, "REPLY:" + parts[1] + ":" + username + ":" + parts[2]);
                        }
                    } else if (message.startsWith("RECALL:")) {
                        // RECALL:uuid
                        String uuid = message.split(":")[1];
                        DatabaseManager.recallMessage(uuid);
                        broadcastToRoom(currentRoom, "RECALL:" + uuid);
                    } else if (message.startsWith("REACT:")) {
                        // REACT:uuid:emoji
                        String[] parts = message.split(":", 3);
                        if (parts.length == 3) {
                            broadcastToRoom(currentRoom, "REACT:" + parts[1] + ":" + username + ":" + parts[2]);
                        }
                    } else {
                        // 6. Tin nhắn chung trong phòng (dành cho fallback)
                        DatabaseManager.saveMessage(username, "[" + currentRoom + "] " + message);
                        broadcastToRoom(currentRoom, username + ":" + message);
                        System.out.println("[" + currentRoom + "] " + username + ": " + message);
                    }
                }
                
            } catch (IOException e) {
                System.out.println(username + " mất kết nối.");
            } finally {
                if (username != null) {
                    removeClientFromCurrentRoom();
                }
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void handleCreateRoom(String rName, String rKey, boolean rHistory, boolean rJoins) {
            if (rooms.containsKey(rName)) {
                writer.println("ROOM_ERROR:Tên phòng đã tồn tại!");
                return;
            }
            Room newRoom = new Room(rName, rKey, this, rHistory, rJoins);
            rooms.put(rName, newRoom);
            changeRoom(rName, newRoom);
            broadcastRoomList();
        }

        private void handleJoinRoom(String rName, String rKey) {
            Room room = rooms.get(rName);
            if (room == null) {
                writer.println("ROOM_ERROR:Phòng không tồn tại!");
                return;
            }
            if (!room.name.equals("Lobby")) {
                if (!room.allowNewJoins) {
                    writer.println("ROOM_ERROR:Phòng đã bị khóa bởi Host.");
                    return;
                }
                if (!room.key.isEmpty() && !room.key.equals(rKey)) {
                    writer.println("ROOM_ERROR:Sai mật khẩu phòng!");
                    return;
                }
            }
            changeRoom(rName, room);
        }

        private void handleUpdateSettings(boolean rHistory, boolean rJoins) {
            Room room = rooms.get(currentRoom);
            if (room != null && room.host == this) {
                room.allowHistory = rHistory;
                room.allowNewJoins = rJoins;
                broadcastToRoom(currentRoom, "SYSTEM: Cài đặt phòng đã được cập nhật bởi Host.");
            }
        }

        private void changeRoom(String rName, Room room) {
            removeClientFromCurrentRoom(); // Rời phòng cũ
            
            currentRoom = rName;
            room.clients.add(this);
            
            writer.println("ROOM_CHANGED:" + currentRoom);
            writer.println("IS_HOST:" + (room.host == this)); // Báo cho client biết mình có phải Host không
            
            // Tải lịch sử nếu được phép
            if (room.allowHistory) {
                synchronized (room.history) {
                    for (String histMsg : room.history) {
                        writer.println(histMsg);
                    }
                }
            }
            
            broadcastToRoom(currentRoom, "SYSTEM: " + username + " đã tham gia phòng.");
            updateUserList(currentRoom);
        }

        private void removeClientFromCurrentRoom() {
            Room room = rooms.get(currentRoom);
            if (room != null) {
                room.clients.remove(this);
                broadcastToRoom(currentRoom, "SYSTEM: " + username + " đã rời phòng.");
                updateUserList(currentRoom);
                
                // Thu hồi phòng nếu trống (trừ Lobby)
                if (room.clients.isEmpty() && !currentRoom.equals("Lobby")) {
                    rooms.remove(currentRoom);
                    broadcastRoomList(); // Cập nhật lại danh sách phòng cho mọi người
                } 
                // Chuyển quyền Host nếu Host rời đi nhưng phòng vẫn còn người
                else if (room.host == this && !room.clients.isEmpty()) {
                    room.host = room.clients.get(0);
                    room.host.sendMessage("IS_HOST:true");
                    broadcastToRoom(currentRoom, "SYSTEM: " + room.host.getUsername() + " được thăng cấp làm Host mới.");
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