import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    private ServerSocket serverSocket;
    private Map<String, Room> rooms = new ConcurrentHashMap<>();

    public ChatServer(int port) {
        try {
            serverSocket = new ServerSocket(port);
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
 // UC07 - Gửi tin nhắn riêng tư
 // 7.1.5. Server tìm người nhận trong danh sách user online và chỉ gửi tin nhắn cho đúng người nhận
    public boolean sendPrivateMessage(ClientHandler senderClient, String receiverUsername, String content) {
        boolean found = false;
        for (Room room : rooms.values()) {
            synchronized (room.clients) {
                for (ClientHandler client : room.clients) {
                    if (receiverUsername.equals(client.getUsername())) {
                    	   // 7.1.6. Gửi tin nhắn riêng đến người nhận và phản hồi lại cho người gửi
                        client.sendMessage("[PRIVATE] " + senderClient.getUsername() + ": " + content);
                        senderClient.sendMessage("[PRIVATE to " + receiverUsername + "] " + content);
                        found = true;
                        break;
                    }
                }
            }
            if (found) break; 
        }

        if (!found) {
        	// 7.1.8b.1. Nếu người nhận không online, hệ thống thông báo lỗi cho người gửi
            senderClient.sendMessage("SYSTEM: Người nhận '" + receiverUsername + "' hiện không online.");
        }
        return found;
    }

    public static void main(String[] args) {
        ChatServer server = new ChatServer(8000);
        server.start();
    }

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

    class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter writer;
        private String username;
        private String currentRoom = "Lobby"; 

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

                String message;
                while ((message = reader.readLine()) != null) {
                	// UC07 - Gửi tin nhắn riêng tư
                	// 7.1.4. Server nhận yêu cầu PRIVATE từ Client và tách thông tin người nhận, nội dung
                    if (message.startsWith("PRIVATE:")) {
                        String[] privateParts = message.split(":", 3);
                        if (privateParts.length == 3) {
                            String receiverUsername = privateParts[1];
                            String privateContent = privateParts[2];
                            
                            boolean sent = sendPrivateMessage(this, receiverUsername, privateContent);
                            if (sent) {
                                // 7.1.7. Chỉ lưu tin nhắn riêng vào database khi gửi thành công
                                DatabaseManager.savePrivateMessage(username, receiverUsername, privateContent);
                                System.out.println("[PRIVATE] " + username + " -> " + receiverUsername + ": " + privateContent);
                            } else {
                                System.out.println("[PRIVATE FAILED] " + username + " -> " + receiverUsername + ": " + privateContent);
                            }
                        } else {
                            // 7.1.4a.2. Nếu định dạng PRIVATE không hợp lệ, hệ thống báo sai định dạng
                            sendMessage("SYSTEM: Sai định dạng tin nhắn riêng.");
                        }
                        continue;
                    } 
                    else if (message.startsWith("/create ")) {
                        String[] parts = message.substring(8).split("\\|");
                        if (parts.length >= 4) {
                            handleCreateRoom(parts[0].trim(), parts[1].trim(), Boolean.parseBoolean(parts[2]), Boolean.parseBoolean(parts[3]));
                        }
                        continue;
                    } 
                    else if (message.startsWith("/join ")) {
                        String[] parts = message.substring(6).split("\\|");
                        handleJoinRoom(parts[0].trim(), parts.length > 1 ? parts[1].trim() : "");
                        continue;
                    } 
                    else if (message.startsWith("/settings ")) {
                        String[] parts = message.substring(10).split("\\|");
                        if (parts.length >= 2) {
                            handleUpdateSettings(Boolean.parseBoolean(parts[0]), Boolean.parseBoolean(parts[1]));
                        }
                        continue;
                    }

                    if (message.startsWith("LOGIN:") || message.startsWith("REG:")) {
                        continue;
                    }

                    // UC 8 - Bước 8.6: Tiếp nhận gói tin nhị phân mã hóa Base64 của File đính kèm từ Client gửi lên
                    if (message.startsWith("FILE:")) {
                        String[] parts = message.split(":", 4);
                        if (parts.length == 4) {
                            String uuid = parts[1];
                            String fileName = parts[2];
                            String base64Data = parts[3];
                            
                            // //8.6.3. Server ghi nhận dữ liệu, gọi tầng DB lưu lại lịch sử tin nhắn dạng text đính kèm
                            DatabaseManager.saveMessage(uuid, username, "[" + currentRoom + "] [Đính kèm file]: " + fileName);
                            
                            // //8.6.4. Server tiến hành broadcast đồng bộ chuỗi dữ liệu FILE này đến tất cả Client trong phòng chat
                            broadcastToRoom(currentRoom, "FILE:" + uuid + ":" + username + ":" + fileName + ":" + base64Data);
                        }
                    } 
                    // UC 11 - Bước 9.3.B4: Tiếp nhận yêu cầu Trả lời tin nhắn (REPLY)
                    else if (message.startsWith("REPLY:")) {
                        String[] parts = message.split(":", 3);
                        if (parts.length == 3) {
                            // //11.9.3.B4.2. Server lưu tin nhắn trích dẫn vào cơ sở dữ liệu qua tầng DatabaseManager
                            DatabaseManager.saveMessage(parts[1], username, "[" + currentRoom + "] [Trả lời]: " + parts[2]);
                            
                            // //11.9.3.B4.3. Server phát gói tin phản hồi REPLY chứa text và quote đồng loạt đến mọi người trong nhóm
                            broadcastToRoom(currentRoom, "REPLY:" + parts[1] + ":" + username + ":" + parts[2]);
                        }
                    } 
                    // UC 11 - Bước 9.3.C4: Tiếp nhận yêu cầu Thu hồi tin nhắn từ người gửi (RECALL)
                    else if (message.startsWith("RECALL:")) {
                        String uuid = message.split(":")[1];
                        
                        // //11.9.3.C4.1. Server xử lý lệnh, gọi sang DBManager cập nhật chuỗi thành "Tin nhắn đã bị thu hồi"
                        DatabaseManager.recallMessage(uuid);
                        
                        // //11.9.3.C4.2. Server broadcast gói lệnh RECALL kèm msgId (UUID) đến toàn bộ Client hiện tại trong phòng
                        broadcastToRoom(currentRoom, "RECALL:" + uuid);
                    } 
                    // UC 11 - Bước 9.3.A2: Tiếp nhận và xử lý yêu cầu Thả cảm xúc tin nhắn (REACT)
                    else if (message.startsWith("REACT:")) {
                        String[] parts = message.split(":", 3);
                        if (parts.length == 3) {
                            // //11.9.3.A2.1. Server xử lý gói cảm xúc và tiến hành broadcast lệnh REACT đến tất cả thành viên trong phòng chat
                            broadcastToRoom(currentRoom, "REACT:" + parts[1] + ":" + username + ":" + parts[2]);
                        }
                    } 
                    // Xử lý gói tin CHAT thông thường (chứa UUID) từ Client gửi lên
                    else if (message.startsWith("CHAT:")) {
                        String[] parts = message.split(":", 3);
                        if (parts.length == 3) {
                            String uuid = parts[1];
                            String chatContent = parts[2];
                            
                            // Sử dụng hàm saveMessage nạp 3 tham số (có UUID) để lưu trữ đồng bộ cho tính năng reply/recall
                            DatabaseManager.saveMessage(uuid, username, "[" + currentRoom + "] " + chatContent);
                            
                            // Broadcast đúng định dạng chuỗi chuẩn: "CHAT:uuid:username:nội_dung"
                            broadcastToRoom(currentRoom, "CHAT:" + uuid + ":" + username + ":" + chatContent);
                        }
                    } else {
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
            removeClientFromCurrentRoom(); 
            
            currentRoom = rName;
            room.clients.add(this);
            
            writer.println("ROOM_CHANGED:" + currentRoom);
            writer.println("IS_HOST:" + (room.host == this)); 
            
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
                
                if (room.clients.isEmpty() && !currentRoom.equals("Lobby")) {
                    rooms.remove(currentRoom);
                    broadcastRoomList(); 
                } 
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