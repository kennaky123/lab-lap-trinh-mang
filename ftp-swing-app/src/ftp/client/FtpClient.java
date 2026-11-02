package ftp.client;

import ftp.common.FtpConfig;
import ftp.common.FtpFileItem;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Động cơ giao thức FTP Client (RFC 959):
 * - Quản lý kết nối điều khiển TCP (Control Connection)
 * - Tự động thiết lập kết nối dữ liệu Passive Mode (PASV Data Connection)
 * - Thực hiện duyệt thư mục, tải xuống (RETR), tải lên (STOR), tạo/xóa thư mục
 */
public class FtpClient {
    public interface FtpLogListener {
        void onCommand(String cmd);
        void onResponse(String resp);
    }

    public interface TransferProgressListener {
        void onProgress(long transferredBytes, long totalBytes);
    }

    private Socket controlSocket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean isConnected = false;
    private final List<FtpLogListener> logListeners = new ArrayList<>();

    public void addLogListener(FtpLogListener listener) {
        logListeners.add(listener);
    }

    /**
     * Kết nối tới FTP Server tại host:port
     */
    public boolean connect(String host, int port) {
        try {
            controlSocket = new Socket();
            controlSocket.connect(new InetSocketAddress(host, port), 10000);

            reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream(), StandardCharsets.UTF_8));

            String welcome = readResponse();
            isConnected = welcome.startsWith("220");
            return isConnected;
        } catch (IOException e) {
            logResponse("Lỗi kết nối tới " + host + ":" + port + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * Xác thực đăng nhập FTP
     */
    public boolean login(String username, String password) {
        if (!isConnected) return false;

        sendCommand("USER " + username);
        String resp = readResponse();
        if (resp.startsWith("331")) {
            sendCommand("PASS " + password);
            resp = readResponse();
        }

        if (resp.startsWith("230")) {
            // Chuyển sang chế độ nhị phân (Binary Mode) cho truyền file an toàn
            sendCommand("TYPE I");
            readResponse();
            return true;
        }
        return false;
    }

    /**
     * Lấy đường dẫn thư mục hiện tại (PWD)
     */
    public String pwd() {
        sendCommand("PWD");
        String resp = readResponse();
        int firstQuote = resp.indexOf('\"');
        int lastQuote = resp.lastIndexOf('\"');
        if (firstQuote != -1 && lastQuote > firstQuote) {
            return resp.substring(firstQuote + 1, lastQuote);
        }
        return "/";
    }

    /**
     * Chuyển thư mục (CWD)
     */
    public boolean cwd(String path) {
        sendCommand("CWD " + path);
        String resp = readResponse();
        return resp.startsWith("250");
    }

    /**
     * Trở về thư mục cha (CDUP)
     */
    public boolean cdup() {
        sendCommand("CDUP");
        String resp = readResponse();
        return resp.startsWith("200") || resp.startsWith("250");
    }

    /**
     * Vào chế độ Passive Mode để lấy địa chỉ Socket truyền dữ liệu
     */
    private InetSocketAddress enterPassiveMode() throws IOException {
        sendCommand("PASV");
        String resp = readResponse();
        if (!resp.startsWith("227")) {
            throw new IOException("Server từ chối lệnh PASV: " + resp);
        }

        Pattern pattern = Pattern.compile("(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)");
        Matcher matcher = pattern.matcher(resp);
        if (matcher.find()) {
            String ip = matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3) + "." + matcher.group(4);
            // Nếu kết nối qua LAN / Wi-Fi mà phản hồi PASV trả về 127.0.0.1 hoặc 0.0.0.0, ưu tiên dùng IP thực của controlSocket
            if (controlSocket != null && controlSocket.getInetAddress() != null) {
                String remoteHost = controlSocket.getInetAddress().getHostAddress();
                if ("127.0.0.1".equals(ip) || "0.0.0.0".equals(ip) || !remoteHost.equals("127.0.0.1")) {
                    ip = remoteHost;
                }
            }
            int port = Integer.parseInt(matcher.group(5)) * 256 + Integer.parseInt(matcher.group(6));
            return new InetSocketAddress(ip, port);
        }
        throw new IOException("Không thể phân tích cú pháp phản hồi PASV: " + resp);
    }

    /**
     * Lấy danh sách tệp tin và thư mục (LIST)
     */
    public List<FtpFileItem> listFiles() throws IOException {
        InetSocketAddress dataAddr = enterPassiveMode();
        Socket dataSocket = new Socket();
        dataSocket.connect(dataAddr, 10000);

        sendCommand("LIST");
        String resp = readResponse();
        if (!resp.startsWith("150")) {
            dataSocket.close();
            throw new IOException("Lỗi phản hồi LIST: " + resp);
        }

        List<FtpFileItem> fileList = new ArrayList<>();
        try (BufferedReader dataReader = new BufferedReader(new InputStreamReader(dataSocket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = dataReader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                FtpFileItem item = parseUnixListing(line);
                if (item != null) {
                    fileList.add(item);
                }
            }
        } finally {
            dataSocket.close();
        }

        readResponse(); // Đọc phản hồi "226 Transfer complete"
        return fileList;
    }

    /**
     * Tải tệp tin từ Server về Client (Download / RETR)
     */
    public boolean downloadFile(String remoteName, File localFile, TransferProgressListener listener) {
        try {
            InetSocketAddress dataAddr = enterPassiveMode();
            Socket dataSocket = new Socket();
            dataSocket.connect(dataAddr, 10000);

            sendCommand("RETR " + remoteName);
            String resp = readResponse();
            if (!resp.startsWith("150")) {
                dataSocket.close();
                return false;
            }

            long totalBytes = 0;
            // Trích xuất kích thước từ chuỗi "(xxxx bytes)" nếu có
            Pattern p = Pattern.compile("\\((\\d+) bytes\\)");
            Matcher m = p.matcher(resp);
            if (m.find()) {
                totalBytes = Long.parseLong(m.group(1));
            }

            long transferred = 0;
            try (InputStream dataIn = dataSocket.getInputStream();
                 OutputStream fileOut = new FileOutputStream(localFile)) {

                byte[] buffer = new byte[FtpConfig.BUFFER_SIZE];
                int read;
                while ((read = dataIn.read(buffer)) != -1) {
                    fileOut.write(buffer, 0, read);
                    transferred += read;
                    if (listener != null) {
                        listener.onProgress(transferred, totalBytes);
                    }
                }
                fileOut.flush();
            } finally {
                dataSocket.close();
            }

            String doneResp = readResponse();
            return doneResp.startsWith("226");
        } catch (Exception e) {
            logResponse("Lỗi khi tải xuống: " + e.getMessage());
            return false;
        }
    }

    /**
     * Tải tệp tin từ Client lên Server (Upload / STOR)
     */
    public boolean uploadFile(File localFile, String remoteName, TransferProgressListener listener) {
        if (!localFile.exists()) return false;
        try {
            InetSocketAddress dataAddr = enterPassiveMode();
            Socket dataSocket = new Socket();
            dataSocket.connect(dataAddr, 10000);

            sendCommand("STOR " + remoteName);
            String resp = readResponse();
            if (!resp.startsWith("150")) {
                dataSocket.close();
                return false;
            }

            long totalBytes = localFile.length();
            long transferred = 0;

            try (InputStream fileIn = new FileInputStream(localFile);
                 OutputStream dataOut = dataSocket.getOutputStream()) {

                byte[] buffer = new byte[FtpConfig.BUFFER_SIZE];
                int read;
                while ((read = fileIn.read(buffer)) != -1) {
                    dataOut.write(buffer, 0, read);
                    transferred += read;
                    if (listener != null) {
                        listener.onProgress(transferred, totalBytes);
                    }
                }
                dataOut.flush();
            } finally {
                dataSocket.close();
            }

            String doneResp = readResponse();
            return doneResp.startsWith("226");
        } catch (Exception e) {
            logResponse("Lỗi khi tải lên: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteFile(String fileName) {
        sendCommand("DELE " + fileName);
        String resp = readResponse();
        return resp.startsWith("250");
    }

    public boolean makeDirectory(String dirName) {
        sendCommand("MKD " + dirName);
        String resp = readResponse();
        return resp.startsWith("257");
    }

    public boolean removeDirectory(String dirName) {
        sendCommand("RMD " + dirName);
        String resp = readResponse();
        return resp.startsWith("250");
    }

    public void disconnect() {
        if (!isConnected) return;
        try {
            sendCommand("QUIT");
            readResponse();
            if (controlSocket != null && !controlSocket.isClosed()) {
                controlSocket.close();
            }
        } catch (Exception ignored) {}
        isConnected = false;
    }

    private void sendCommand(String cmd) {
        try {
            writer.write(cmd + "\r\n");
            writer.flush();
            logCommand(cmd.startsWith("PASS") ? "PASS ********" : cmd);
        } catch (IOException e) {
            logResponse("Lỗi gửi lệnh: " + e.getMessage());
        }
    }

    private String readResponse() {
        try {
            StringBuilder sb = new StringBuilder();
            String line = reader.readLine();
            if (line == null) return "";
            sb.append(line);
            logResponse(line);

            // Kiểm tra phản hồi nhiều dòng (dạng 123-abc ... 123 end)
            if (line.length() >= 4 && line.charAt(3) == '-') {
                String code = line.substring(0, 3);
                while ((line = reader.readLine()) != null) {
                    sb.append("\n").append(line);
                    logResponse(line);
                    if (line.startsWith(code + " ")) {
                        break;
                    }
                }
            }
            return sb.toString();
        } catch (IOException e) {
            logResponse("Lỗi nhận phản hồi: " + e.getMessage());
            return "";
        }
    }

    private void logCommand(String cmd) {
        for (FtpLogListener l : logListeners) {
            l.onCommand(cmd);
        }
    }

    private void logResponse(String resp) {
        for (FtpLogListener l : logListeners) {
            l.onResponse(resp);
        }
    }

    private FtpFileItem parseUnixListing(String line) {
        // Ví dụ: drwxr-xr-x 1 owner group          0 Oct 30 11:20 documents
        // Ví dụ: -rw-r--r-- 1 owner group       1234 Oct 30 11:20 README.txt
        try {
            String[] tokens = line.split("\\s+");
            if (tokens.length >= 9) {
                boolean isDir = tokens[0].startsWith("d");
                long size = Long.parseLong(tokens[4]);
                StringBuilder nameBuilder = new StringBuilder();
                for (int i = 8; i < tokens.length; i++) {
                    if (nameBuilder.length() > 0) nameBuilder.append(" ");
                    nameBuilder.append(tokens[i]);
                }
                String name = nameBuilder.toString();
                FtpFileItem item = new FtpFileItem(name, size, isDir, System.currentTimeMillis());
                item.setPermissions(tokens[0]);
                return item;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public boolean isConnected() {
        return isConnected && controlSocket != null && !controlSocket.isClosed();
    }
}

