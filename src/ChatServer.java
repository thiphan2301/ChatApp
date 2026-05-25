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
            try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
            ) {
                writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println("Enter your username:");
                username = reader.readLine();
                broadcast(username + " has joined the chat!");
                updateUserList();
                String message;
                while ((message = reader.readLine()) != null) {
                    broadcast(username + ":" + message); // send to everyone
                    System.out.println(username + ": " + message);
                }
            } catch (IOException e) {
                System.out.println(username + " disconnected.");
            } finally {
                removeClient(this);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
        public void sendMessage(String message) {
            if (writer != null) {
                writer.println(message);
            }
        }
    }
}
