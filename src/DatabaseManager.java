import java.sql.*;
import java.security.MessageDigest;
import java.util.Base64;

public class DatabaseManager {
    private static final String URL = "jdbc:mysql://localhost:3306/chatapp";
    private static final String USER = "root";
    private static final String PASSWORD = "";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static boolean verifyUser(String username, String password) {
        String query = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, username);
            ps.setString(2, hashPassword(password));  
            ResultSet rs = ps.executeQuery();
            return rs.next(); 
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean registerUser(String username, String password) {
        String query = "INSERT INTO users (username, password) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, username);
            ps.setString(2, hashPassword(password)); 
            
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
             e.printStackTrace();
            return false; 
        }
    }

    public static void saveMessage(String senderName, String content) {
        saveMessage(java.util.UUID.randomUUID().toString(), senderName, content);
    }

    // UC 8 & UC 11: Hàm dùng chung để lưu vết thông tin bản ghi nội dung/file đính kèm vào bảng messages
    public static void saveMessage(String msgId, String senderName, String content) {
        // //8.6.3.1. Truy vấn INSERT dữ liệu lịch sử tin nhắn kèm ID định danh UUID vào MySQL Database
        // //11.9.3.B4.2.1. Truy vấn INSERT dữ liệu tin nhắn phản hồi kèm theo câu trích dẫn nội dung gốc
        String query = "INSERT INTO messages (msg_id, sender_id, content) " +
                       "VALUES (?, (SELECT id FROM users WHERE username = ?), ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, msgId);
            ps.setString(2, senderName);
            ps.setString(3, content);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Lỗi lưu DB.");
        }
    }

    // UC 11 - Bước 9.3.C4: Thực hiện chỉnh sửa dữ liệu khi người dùng thu hồi tin nhắn
    public static void recallMessage(String msgId) {
        // //11.9.3.C4.1.1. Thực thi truy vấn UPDATE trường content của dòng tin nhắn chỉ định thành chuỗi thông báo thay thế
        String query = "UPDATE messages SET content = 'Tin nhắn đã bị thu hồi' WHERE msg_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, msgId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(password.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
            }
    }

    public static void savePrivateMessage(String msgId, String senderName, String receiverName, String content) {
        String query = "INSERT INTO messages (msg_id, sender_id, receiver_id, content, created_at) " +
                       "VALUES (?, " +
                       "(SELECT id FROM users WHERE username = ?), " +
                       "(SELECT id FROM users WHERE username = ?), " +
                       "?, NOW())";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, msgId);
            ps.setString(2, senderName);
            ps.setString(3, receiverName);
            ps.setString(4, content);
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void savePrivateMessage(String senderName, String receiverName, String content) {
        savePrivateMessage(java.util.UUID.randomUUID().toString(), senderName, receiverName, content);
    }
}