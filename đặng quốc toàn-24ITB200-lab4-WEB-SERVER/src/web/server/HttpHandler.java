package web.server;

import web.common.HttpConstants;
import web.common.HttpRequest;
import web.common.HttpResponse;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Map;

/**
 * Xử lý một yêu cầu HTTP Request từ Client:
 * - Hỗ trợ GET: Phục vụ tệp tĩnh, PDF, MP3, MP4
 * - Hỗ trợ HEAD: Chỉ trả về HTTP Headers (kiểm tra tính khả dụng của tài nguyên)
 * - Hỗ trợ POST: Tiếp nhận và xử lý dữ liệu Form người dùng
 */
public class HttpHandler implements Runnable {
    private final Socket clientSocket;
    private final HttpServer server;
    private final String clientIp;

    public HttpHandler(Socket socket, HttpServer server) {
        this.clientSocket = socket;
        this.server = server;
        this.clientIp = socket.getInetAddress().getHostAddress();
    }

    @Override
    public void run() {
        try (InputStream in = clientSocket.getInputStream();
             OutputStream out = clientSocket.getOutputStream()) {

            HttpRequest req = HttpRequest.parse(in);
            if (req == null) return;

            String method = req.getMethod();
            String uri = req.getCleanPath();

            server.log("[" + method + "] " + uri + " từ " + clientIp);

            if ("GET".equalsIgnoreCase(method)) {
                handleGetOrHead(req, out, true);
            } else if ("HEAD".equalsIgnoreCase(method)) {
                handleGetOrHead(req, out, false); // HEAD không gửi body
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePost(req, out);
            } else {
                HttpResponse resp = HttpResponse.createHtml(405, "<h1>405 Method Not Allowed</h1>");
                resp.writeTo(out, true);
                server.recordAccess(method, uri, 405, clientIp);
            }

        } catch (Exception e) {
            server.log("[LỖI XỬ LÝ HTTP]: " + e.getMessage());
        } finally {
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException ignored) {}
        }
    }

    private void handleGetOrHead(HttpRequest req, OutputStream out, boolean sendBody) throws IOException {
        String path = req.getCleanPath();
        if ("/".equals(path)) {
            path = "/index.html";
        }

        File file = new File(HttpConstants.WEB_ROOT, path.replace('/', File.separatorChar));

        // Kiểm tra an toàn chống Directory Traversal
        File rootDir = new File(HttpConstants.WEB_ROOT).getCanonicalFile();
        File canonicalTarget = file.getCanonicalFile();
        if (!canonicalTarget.getPath().startsWith(rootDir.getPath()) || !canonicalTarget.exists() || canonicalTarget.isDirectory()) {
            HttpResponse resp = HttpResponse.create404(req.getUri());
            resp.writeTo(out, sendBody);
            server.recordAccess(req.getMethod(), req.getUri(), 404, clientIp);
            return;
        }

        long fileLength = canonicalTarget.length();
        String mimeType = HttpConstants.getMimeType(canonicalTarget.getName());

        // Kiểm tra Range Header phục vụ Streaming Media (MP3, MP4)
        String rangeHeader = req.getHeader("range");
        if (rangeHeader != null && rangeHeader.startsWith("bytes=") && sendBody) {
            handlePartialContent(canonicalTarget, mimeType, rangeHeader, out, req.getMethod(), req.getUri());
            return;
        }

        HttpResponse resp = new HttpResponse(200);
        resp.setContentType(mimeType);
        resp.setHeader("Content-Length", String.valueOf(fileLength));
        resp.setHeader("Accept-Ranges", "bytes");

        // Thiết lập header gợi ý tải xuống nếu là file tài liệu hoặc đa phương tiện
        if (mimeType.startsWith("application/") || mimeType.startsWith("audio/") || mimeType.startsWith("video/")) {
            resp.setHeader("Content-Disposition", "inline; filename=\"" + canonicalTarget.getName() + "\"");
        }

        if (sendBody) {
            byte[] fileBytes = Files.readAllBytes(canonicalTarget.toPath());
            resp.setBody(fileBytes);
        }

        resp.writeTo(out, sendBody);
        server.recordAccess(req.getMethod(), req.getUri(), 200, clientIp);
        server.recordServiceAccess(canonicalTarget.getName(), fileLength);
    }

    private void handlePartialContent(File file, String mimeType, String rangeHeader, OutputStream out, String method, String uri) throws IOException {
        long fileLength = file.length();
        String range = rangeHeader.substring(6).trim();
        long start = 0;
        long end = fileLength - 1;

        int dash = range.indexOf('-');
        if (dash != -1) {
            String sStart = range.substring(0, dash).trim();
            String sEnd = range.substring(dash + 1).trim();
            if (!sStart.isEmpty()) start = Long.parseLong(sStart);
            if (!sEnd.isEmpty()) end = Long.parseLong(sEnd);
        }

        if (start > end || start >= fileLength) {
            HttpResponse resp = new HttpResponse(416); // Range Not Satisfiable
            resp.setHeader("Content-Range", "bytes */" + fileLength);
            resp.writeTo(out, false);
            return;
        }

        long contentLength = end - start + 1;
        HttpResponse resp = new HttpResponse(206); // Partial Content
        resp.setContentType(mimeType);
        resp.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
        resp.setHeader("Content-Length", String.valueOf(contentLength));
        resp.setHeader("Accept-Ranges", "bytes");

        byte[] buffer = new byte[(int) contentLength];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(start);
            raf.readFully(buffer);
        }
        resp.setBody(buffer);
        resp.writeTo(out, true);

        server.recordAccess(method, uri, 206, clientIp);
        server.recordServiceAccess(file.getName(), contentLength);
    }

    private void handlePost(HttpRequest req, OutputStream out) throws IOException {
        String path = req.getCleanPath();

        if ("/submit-feedback".equalsIgnoreCase(path)) {
            Map<String, String> params = req.getFormParams();
            String name = params.getOrDefault("student_name", "Không rõ");
            String id = params.getOrDefault("student_id", "Chưa nhập");
            String content = params.getOrDefault("content", "Trống");

            // Lưu dữ liệu POST vào Server
            server.savePostSubmission(name, id, content, clientIp);

            String responseHtml = "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Tiếp Nhận POST Thành Công</title>"
                    + "<style>body{font-family: Arial; padding: 40px; background: #f8f9fa; text-align: center;}"
                    + ".card{background: white; max-width: 500px; margin: 0 auto; padding: 25px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);}"
                    + "h2{color: #28a745;}</style></head><body>"
                    + "<div class='card'>"
                    + "<h2>✔ Tiếp Nhận Yêu Cầu HTTP POST Thành Công!</h2>"
                    + "<p>Dữ liệu Form của bạn đã được Web Server phân tích và ghi vào hệ thống.</p>"
                    + "<div style='text-align: left; background: #f1f3f4; padding: 12px; border-radius: 4px;'>"
                    + "<b>Họ và tên:</b> " + name + "<br>"
                    + "<b>MSSV:</b> " + id + "<br>"
                    + "<b>Nội dung:</b> " + content + "<br>"
                    + "<b>Địa chỉ IP:</b> " + clientIp + "<br>"
                    + "</div>"
                    + "<br><a href='/' style='color: #1a73e8; text-decoration: none; font-weight: bold;'>Quay về Trang Chủ</a>"
                    + "</div></body></html>";

            HttpResponse resp = HttpResponse.createHtml(200, responseHtml);
            resp.writeTo(out, true);
            server.recordAccess("POST", path, 200, clientIp);
        } else {
            // Echo raw POST
            String echoHtml = "<h1>HTTP POST Received</h1><pre>" + req.getBody() + "</pre>";
            HttpResponse resp = HttpResponse.createHtml(200, echoHtml);
            resp.writeTo(out, true);
            server.recordAccess("POST", path, 200, clientIp);
        }
    }
}

