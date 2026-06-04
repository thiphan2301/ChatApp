
import java.sql.*;

public class DatabaseManager {
    private static final String URL = "jdbc:mysql://localhost:3306/chatapp";
    private static final String USER = "root";
    private static final String PASSWORD = "";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    // Hàm kiểm tra Đăng nhập
    public static boolean verifyUser(String username, String password) {
        String query = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            return rs.next(); // Trả về true nếu tìm thấy tài khoản hợp lệ
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Hàm Đăng ký tài khoản mới
    public static boolean registerUser(String username, String password) {
        String query = "INSERT INTO users (username, password) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, username);
            ps.setString(2, password);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
             e.printStackTrace();
            return false; // Thất bại nếu trùng username 
        }
    }

    // Hàm lưu tin nhắn vào lịch sử
    public static void saveMessage(String senderName, String content) {
        String query = "INSERT INTO messages (sender_id, content) " +
                       "VALUES ((SELECT id FROM users WHERE username = ?), ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, senderName);
            ps.setString(2, content);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
 // Hàm lưu tin nhắn riêng vào lịch sử
    public static void savePrivateMessage(String senderName, String receiverName, String content) {
        String query = "INSERT INTO messages (sender_id, receiver_id, content, created_at) " +
                       "VALUES (" +
                       "(SELECT id FROM users WHERE username = ?), " +
                       "(SELECT id FROM users WHERE username = ?), " +
                       "?, NOW())";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, senderName);
            ps.setString(2, receiverName);
            ps.setString(3, content);
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}