package ftp.server;

import ftp.common.FtpConfig;
import ftp.common.FtpFileItem;
import ftp.common.FtpUser;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Xử lý phiên kết nối của một Client theo đúng chuẩn giao thức FTP (RFC 959).
 * Hỗ trợ các lệnh: USER, PASS, PWD, CWD, CDUP, TYPE, PASV, EPSV, PORT, LIST,
 * RETR (Download), STOR (Upload), DELE (Delete), MKD (Make Dir), RMD (Remove Dir),
 * SIZE, SYST, FEAT, NOOP, QUIT.
 */
public class FtpClientHandler implements Runnable {
    private final Socket controlSocket;
    private final FtpServer server;
    private BufferedReader reader;
    private BufferedWriter writer;

    private String clientIp;
    private FtpUser currentUser;
    private String tempUsername;
    private boolean isLoggedIn = false;

    // Quản lý đường dẫn thư mục hiện tại của người dùng
    private File currentDirectory;
    private File userRootDir;

    // Chế độ truyền dữ liệu Data Connection (Active / Passive)
    private ServerSocket passiveDataServerSocket;
    private Socket activeDataSocket;
    private String activeDataHost;
    private int activeDataPort = -1;
    private boolean isPassiveMode = false;

    public FtpClientHandler(Socket socket, FtpServer server) {
        this.controlSocket = socket;
        this.server = server;
        this.clientIp = socket.getInetAddress().getHostAddress();
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream(), StandardCharsets.UTF_8));

            // Gửi thông điệp chào mừng chuẩn FTP
            sendResponse("220 Chào mừng bạn đến với Java Swing FTP Server (RFC 959 Ready)");

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                server.log("[COMMAND từ " + clientIp + "]: " + (line.toUpperCase().startsWith("PASS") ? "PASS ********" : line));
                handleCommand(line);
                if ("QUIT".equalsIgnoreCase(line)) {
                    break;
                }
            }
        } catch (SocketException ignored) {
        } catch (Exception e) {
            server.log("[ERROR] Lỗi phiên FTP với " + clientIp + ": " + e.getMessage());
        } finally {
            closeSession();
        }
    }

    private void handleCommand(String line) throws IOException {
        String command = line;
        String argument = "";
        int spaceIndex = line.indexOf(' ');
        if (spaceIndex != -1) {
            command = line.substring(0, spaceIndex).toUpperCase();
            argument = line.substring(spaceIndex + 1).trim();
        } else {
            command = line.toUpperCase();
        }

        switch (command) {
            case "USER":
                tempUsername = argument;
                if ("anonymous".equalsIgnoreCase(argument)) {
                    sendResponse("331 Anonymous login ok, send your complete email as password");
                } else {
                    sendResponse("331 Cần mật khẩu cho tài khoản " + argument);
                }
                break;

            case "PASS":
                handlePass(argument);
                break;

            case "PWD":
            case "XPWD":
                if (!checkLoggedIn()) return;
                String relativePath = getRelativePath(currentDirectory);
                sendResponse("257 \"" + relativePath + "\" là thư mục hiện tại");
                break;

            case "CWD":
            case "XCWD":
                if (!checkLoggedIn()) return;
                handleCwd(argument);
                break;

            case "CDUP":
            case "XCUP":
                if (!checkLoggedIn()) return;
                handleCdup();
                break;

            case "TYPE":
                // Hỗ trợ chế độ ASCII (A) và Binary (I)
                sendResponse("200 Đã chuyển sang chế độ TYPE " + argument);
                break;

            case "AUTH":
                sendResponse("502 Explicit TLS/SSL is not supported, please use plain FTP");
                break;

            case "OPTS":
                if (argument.toUpperCase().startsWith("UTF8") || argument.toUpperCase().startsWith("UTF-8")) {
                    sendResponse("200 Always in UTF-8 mode.");
                } else {
                    sendResponse("502 Lệnh OPTS không được hỗ trợ (" + argument + ")");
                }
                break;

            case "PASV":
                if (!checkLoggedIn()) return;
                handlePasv();
                break;

            case "EPSV":
                if (!checkLoggedIn()) return;
                handleEpsv();
                break;

            case "PORT":
                if (!checkLoggedIn()) return;
                handlePort(argument);
                break;

            case "LIST":
            case "NLST":
                if (!checkLoggedIn()) return;
                handleList();
                break;

            case "RETR":
                if (!checkLoggedIn()) return;
                handleRetr(argument);
                break;

            case "STOR":
                if (!checkLoggedIn()) return;
                handleStor(argument);
                break;

            case "DELE":
                if (!checkLoggedIn()) return;
                handleDele(argument);
                break;

            case "MKD":
            case "XMKD":
                if (!checkLoggedIn()) return;
                handleMkd(argument);
                break;

            case "RMD":
            case "XRMD":
                if (!checkLoggedIn()) return;
                handleRmd(argument);
                break;

            case "SIZE":
                if (!checkLoggedIn()) return;
                handleSize(argument);
                break;

            case "SYST":
                sendResponse("215 UNIX Type: L8");
                break;

            case "FEAT":
                sendResponse("211-Features:\r\n PASV\r\n EPSV\r\n SIZE\r\n211 End");
                break;

            case "NOOP":
                sendResponse("200 NOOP thành công");
                break;

            case "QUIT":
                sendResponse("221 Tạm biệt, đóng kết nối điều khiển");
                break;

            default:
                sendResponse("502 Lệnh không được hỗ trợ (" + command + ")");
                break;
        }
    }

    private void handlePass(String password) {
        if (tempUsername == null) {
            sendResponse("503 Vui lòng gửi lệnh USER trước");
            return;
        }

        FtpUser user = FtpDatabaseManager.getInstance().authenticate(tempUsername, password);
        if (user != null) {
            this.currentUser = user;
            this.isLoggedIn = true;

            // Thiết lập thư mục gốc cho tài khoản
            File root = new File(currentUser.getHomeDirectory());
            if (!root.exists()) {
                File defaultRoot = new File(FtpConfig.SERVER_ROOT_DIR);
                if (defaultRoot.exists()) {
                    root = defaultRoot;
                } else {
                    root.mkdirs();
                }
            }
            this.userRootDir = root;
            this.currentDirectory = root;

            server.onUserLogin(this);
            FtpDatabaseManager.getInstance().logActivity(currentUser.getUsername(), "LOGIN", "", 0, clientIp, true, "Đăng nhập thành công");
            sendResponse("230 Đăng nhập thành công. Quyền hạn: " + currentUser.getPermissionsSummary());
        } else {
            FtpDatabaseManager.getInstance().logActivity(tempUsername, "LOGIN_FAILED", "", 0, clientIp, false, "Sai mật khẩu hoặc bị khóa");
            sendResponse("530 Đăng nhập thất bại, sai tên tài khoản hoặc mật khẩu");
        }
    }

    private boolean checkLoggedIn() {
        if (!isLoggedIn) {
            sendResponse("530 Chưa đăng nhập, vui lòng thực hiện USER và PASS trước");
            return false;
        }
        return true;
    }

    private void handleCwd(String targetDir) {
        File next;
        if (targetDir.startsWith("/") || targetDir.startsWith("\\")) {
            next = new File(userRootDir, targetDir.substring(1));
        } else {
            next = new File(currentDirectory, targetDir);
        }

        // Chống lỗ hổng Directory Traversal vượt ra ngoài userRootDir
        if (isWithinRoot(next) && next.exists() && next.isDirectory()) {
            this.currentDirectory = next;
            sendResponse("250 Thư mục hiện tại đã chuyển thành " + getRelativePath(currentDirectory));
        } else {
            sendResponse("550 Không tìm thấy thư mục hoặc không có quyền truy cập");
        }
    }

    private void handleCdup() {
        File parent = currentDirectory.getParentFile();
        if (parent != null && isWithinRoot(parent)) {
            currentDirectory = parent;
            sendResponse("200 Chuyển về thư mục cha thành công: " + getRelativePath(currentDirectory));
        } else {
            sendResponse("550 Đã ở thư mục gốc, không thể lên cao hơn");
        }
    }

    /**
     * Mở cổng Passive Data Connection (PASV)
     */
    private void handlePasv() {
        closeDataConnections();
        try {
            // Tìm một cổng trống trong dải PASV
            int port = findAvailablePort(FtpConfig.PASV_PORT_MIN, FtpConfig.PASV_PORT_MAX);
            passiveDataServerSocket = new ServerSocket(port);
            passiveDataServerSocket.setSoTimeout(15000); // Timeout 15s chờ client kết nối
            isPassiveMode = true;

            // Tính toán địa chỉ IP và Port cho cú pháp: (h1,h2,h3,h4,p1,p2)
            InetAddress localAddr = controlSocket.getLocalAddress();
            String hostAddress = localAddr != null ? localAddr.getHostAddress() : "";
            String[] parts = hostAddress.split("\\.");
            if (parts.length != 4 || "0.0.0.0".equals(hostAddress)) {
                String lanIp = FtpServer.getLocalLanIp();
                parts = lanIp.split("\\.");
                if (parts.length != 4) {
                    parts = new String[]{"127", "0", "0", "1"};
                }
            }
            int p1 = port / 256;
            int p2 = port % 256;

            String pasvReply = String.format("227 Vào chế độ Passive Mode (%s,%s,%s,%s,%d,%d)",
                    parts[0], parts[1], parts[2], parts[3], p1, p2);
            sendResponse(pasvReply);
        } catch (IOException e) {
            sendResponse("425 Không thể mở cổng dữ liệu Passive: " + e.getMessage());
        }
    }

    private void handleEpsv() {
        closeDataConnections();
        try {
            int port = findAvailablePort(FtpConfig.PASV_PORT_MIN, FtpConfig.PASV_PORT_MAX);
            passiveDataServerSocket = new ServerSocket(port);
            passiveDataServerSocket.setSoTimeout(15000);
            isPassiveMode = true;
            sendResponse("229 Entering Extended Passive Mode (|||" + port + "|)");
        } catch (IOException e) {
            sendResponse("425 Không thể mở cổng dữ liệu EPSV: " + e.getMessage());
        }
    }

    private void handlePort(String arg) {
        closeDataConnections();
        try {
            String[] parts = arg.split(",");
            if (parts.length == 6) {
                activeDataHost = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
                activeDataPort = Integer.parseInt(parts[4]) * 256 + Integer.parseInt(parts[5]);
                isPassiveMode = false;
                sendResponse("200 Lệnh PORT thành công");
            } else {
                sendResponse("501 Cú pháp PORT không hợp lệ");
            }
        } catch (Exception e) {
            sendResponse("501 Lỗi phân tích PORT: " + e.getMessage());
        }
    }

    /**
     * Mở socket kênh dữ liệu (Data Socket) cho việc truyền tải
     */
    private Socket openDataConnection() throws IOException {
        if (isPassiveMode) {
            if (passiveDataServerSocket != null && !passiveDataServerSocket.isClosed()) {
                Socket dataSock = passiveDataServerSocket.accept();
                return dataSock;
            }
            throw new IOException("Passive Data ServerSocket chưa được khởi tạo!");
        } else {
            if (activeDataHost != null && activeDataPort > 0) {
                return new Socket(activeDataHost, activeDataPort);
            }
            throw new IOException("Chưa thiết lập địa chỉ Active Data Port!");
        }
    }

    /**
     * Xử lý lệnh LIST: gửi danh sách thư mục và file qua Data Connection
     */
    private void handleList() {
        sendResponse("150 Mở kênh truyền dữ liệu cho danh sách thư mục");
        try (Socket dataSocket = openDataConnection();
             BufferedWriter dataWriter = new BufferedWriter(new OutputStreamWriter(dataSocket.getOutputStream(), StandardCharsets.UTF_8))) {

            File[] files = currentDirectory.listFiles();
            if (files != null) {
                for (File f : files) {
                    FtpFileItem item = new FtpFileItem(f.getName(), f.length(), f.isDirectory(), f.lastModified());
                    dataWriter.write(item.toFtpListFormat());
                }
            }
            dataWriter.flush();
            sendResponse("226 Hoàn tất gửi danh sách thư mục");
        } catch (Exception e) {
            sendResponse("426 Lỗi khi truyền danh sách: " + e.getMessage());
        } finally {
            closeDataConnections();
        }
    }

    /**
     * Xử lý lệnh RETR: Tải file từ Server về Client (Download)
     */
    private void handleRetr(String fileName) {
        if (!currentUser.isCanRead()) {
            sendResponse("550 Quyền hạn bị từ chối: Bạn không có quyền tải xuống (Read Permission)");
            FtpDatabaseManager.getInstance().logActivity(currentUser.getUsername(), "RETR_DENIED", fileName, 0, clientIp, false, "Thiếu quyền Read");
            return;
        }

        File targetFile = new File(currentDirectory, fileName);
        if (!isWithinRoot(targetFile) || !targetFile.exists() || targetFile.isDirectory()) {
            sendResponse("550 Không tìm thấy tệp tin: " + fileName);
            return;
        }

        sendResponse("150 Bắt đầu gửi tệp tin " + fileName + " (" + targetFile.length() + " bytes)");

        long bytesSent = 0;
        try (Socket dataSocket = openDataConnection();
             InputStream fileIn = new FileInputStream(targetFile);
             OutputStream dataOut = dataSocket.getOutputStream()) {

            byte[] buffer = new byte[FtpConfig.BUFFER_SIZE];
            int read;
            while ((read = fileIn.read(buffer)) != -1) {
                dataOut.write(buffer, 0, read);
                bytesSent += read;
            }
            dataOut.flush();
            sendResponse("226 Tải xuống hoàn tất (" + bytesSent + " bytes)");

            server.onFileTransferred("DOWNLOAD", fileName, bytesSent, currentUser.getUsername());
            FtpDatabaseManager.getInstance().logActivity(currentUser.getUsername(), "DOWNLOAD", fileName, bytesSent, clientIp, true, "Tải xuống thành công");
        } catch (Exception e) {
            sendResponse("426 Gián đoạn quá trình tải tệp tin: " + e.getMessage());
            FtpDatabaseManager.getInstance().logActivity(currentUser.getUsername(), "DOWNLOAD_FAILED", fileName, bytesSent, clientIp, false, e.getMessage());
        } finally {
            closeDataConnections();
        }
    }

    /**
     * Xử lý lệnh STOR: Tải file từ Client lên Server (Upload)
     */
    private void handleStor(String fileName) {
        if (!currentUser.isCanWrite()) {
            sendResponse("550 Quyền hạn bị từ chối: Bạn không có quyền tải lên (Write Permission)");
            FtpDatabaseManager.getInstance().logActivity(currentUser.getUsername(), "STOR_DENIED", fileName, 0, clientIp, false, "Thiếu quyền Write");
            return;
        }

        File targetFile = new File(currentDirectory, fileName);
        if (!isWithinRoot(targetFile)) {
            sendResponse("550 Đường dẫn không hợp lệ");
            return;
        }

        sendResponse("150 Sẵn sàng nhận dữ liệu tệp tin " + fileName);

        long bytesReceived = 0;
        try (Socket dataSocket = openDataConnection();
             InputStream dataIn = dataSocket.getInputStream();
             OutputStream fileOut = new FileOutputStream(targetFile)) {

            byte[] buffer = new byte[FtpConfig.BUFFER_SIZE];
            int read;
            while ((read = dataIn.read(buffer)) != -1) {
                fileOut.write(buffer, 0, read);
                bytesReceived += read;
            }
            fileOut.flush();
            sendResponse("226 Tải lên hoàn tất (" + bytesReceived + " bytes)");

            server.onFileTransferred("UPLOAD", fileName, bytesReceived, currentUser.getUsername());
            FtpDatabaseManager.getInstance().logActivity(currentUser.getUsername(), "UPLOAD", fileName, bytesReceived, clientIp, true, "Tải lên thành công");
        } catch (Exception e) {
            sendResponse("426 Lỗi trong quá trình nhận tệp tin: " + e.getMessage());
            FtpDatabaseManager.getInstance().logActivity(currentUser.getUsername(), "UPLOAD_FAILED", fileName, bytesReceived, clientIp, false, e.getMessage());
        } finally {
            closeDataConnections();
        }
    }

    private void handleDele(String fileName) {
        if (!currentUser.isCanDelete()) {
            sendResponse("550 Quyền hạn bị từ chối: Bạn không có quyền xóa tệp tin (Delete Permission)");
            FtpDatabaseManager.getInstance().logActivity(currentUser.getUsername(), "DELE_DENIED", fileName, 0, clientIp, false, "Thiếu quyền Delete");
            return;
        }

        File targetFile = new File(currentDirectory, fileName);
        if (isWithinRoot(targetFile) && targetFile.exists() && targetFile.isFile()) {
            if (targetFile.delete()) {
                sendResponse("250 Xóa tệp tin " + fileName + " thành công");
                FtpDatabaseManager.getInstance().logActivity(currentUser.getUsername(), "DELETE_FILE", fileName, 0, clientIp, true, "Đã xóa");
            } else {
                sendResponse("550 Không thể xóa tệp tin");
            }
        } else {
            sendResponse("550 Tệp tin không tồn tại");
        }
    }

    private void handleMkd(String dirName) {
        if (!currentUser.isCanWrite()) {
            sendResponse("550 Quyền hạn bị từ chối: Không có quyền tạo thư mục");
            return;
        }

        File newDir = new File(currentDirectory, dirName);
        if (isWithinRoot(newDir) && !newDir.exists()) {
            if (newDir.mkdir()) {
                sendResponse("257 \"" + getRelativePath(newDir) + "\" đã được tạo thành công");
                FtpDatabaseManager.getInstance().logActivity(currentUser.getUsername(), "CREATE_DIR", dirName, 0, clientIp, true, "Đã tạo thư mục");
            } else {
                sendResponse("550 Tạo thư mục thất bại");
            }
        } else {
            sendResponse("550 Thư mục đã tồn tại hoặc đường dẫn không hợp lệ");
        }
    }

    private void handleRmd(String dirName) {
        if (!currentUser.isCanDelete()) {
            sendResponse("550 Quyền hạn bị từ chối: Không có quyền xóa thư mục");
            return;
        }

        File targetDir = new File(currentDirectory, dirName);
        if (isWithinRoot(targetDir) && targetDir.exists() && targetDir.isDirectory()) {
            if (targetDir.delete()) {
                sendResponse("250 Đã xóa thư mục " + dirName);
                FtpDatabaseManager.getInstance().logActivity(currentUser.getUsername(), "REMOVE_DIR", dirName, 0, clientIp, true, "Đã xóa thư mục");
            } else {
                sendResponse("550 Không thể xóa thư mục (Thư mục phải rỗng)");
            }
        } else {
            sendResponse("550 Thư mục không tồn tại");
        }
    }

    private void handleSize(String fileName) {
        File f = new File(currentDirectory, fileName);
        if (isWithinRoot(f) && f.exists() && f.isFile()) {
            sendResponse("213 " + f.length());
        } else {
            sendResponse("550 Không xác định được kích thước");
        }
    }

    private boolean isWithinRoot(File file) {
        try {
            String rootPath = userRootDir.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            return filePath.startsWith(rootPath);
        } catch (IOException e) {
            return false;
        }
    }

    private String getRelativePath(File file) {
        try {
            String rootPath = userRootDir.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (filePath.equals(rootPath)) return "/";
            String rel = filePath.substring(rootPath.length()).replace('\\', '/');
            return rel.startsWith("/") ? rel : "/" + rel;
        } catch (IOException e) {
            return "/";
        }
    }

    private int findAvailablePort(int min, int max) {
        Random rand = new Random();
        for (int i = 0; i < 50; i++) {
            int port = min + rand.nextInt(max - min);
            try (ServerSocket ss = new ServerSocket(port)) {
                return port;
            } catch (IOException ignored) {}
        }
        return min; // Fallback
    }

    private void sendResponse(String response) {
        try {
            writer.write(response + "\r\n");
            writer.flush();
            server.log("[REPLY tới " + clientIp + "]: " + response);
        } catch (IOException e) {
            server.log("[ERROR] Gửi phản hồi thất bại: " + e.getMessage());
        }
    }

    private void closeDataConnections() {
        try {
            if (passiveDataServerSocket != null && !passiveDataServerSocket.isClosed()) {
                passiveDataServerSocket.close();
            }
            if (activeDataSocket != null && !activeDataSocket.isClosed()) {
                activeDataSocket.close();
            }
        } catch (IOException ignored) {}
        passiveDataServerSocket = null;
        activeDataSocket = null;
    }

    public void closeSession() {
        closeDataConnections();
        try {
            if (controlSocket != null && !controlSocket.isClosed()) {
                controlSocket.close();
            }
        } catch (IOException ignored) {}
        server.onUserDisconnect(this);
    }

    public String getUsername() {
        return currentUser != null ? currentUser.getUsername() : "Chưa đăng nhập";
    }

    public String getClientIp() {
        return clientIp;
    }
}

