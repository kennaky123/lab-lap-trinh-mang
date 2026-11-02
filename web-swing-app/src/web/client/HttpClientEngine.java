package web.client;

import web.common.HttpConstants;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Động cơ Client thực thi giao thức HTTP/1.1:
 * - Hỗ trợ các phương thức: GET, HEAD, POST
 * - Hỗ trợ tải tệp tin (File Download) với bộ đếm tiến độ
 */
public class HttpClientEngine {

    public interface DownloadProgressListener {
        void onProgress(long transferredBytes, long totalBytes);
    }

    public static class HttpResult {
        public int statusCode;
        public String statusMessage;
        public Map<String, List<String>> headers = new LinkedHashMap<>();
        public String body;
        public byte[] rawBytes;

        public String getHeaderString() {
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP ").append(statusCode).append(" ").append(statusMessage).append("\n");
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() != null) {
                    sb.append(e.getKey()).append(": ").append(String.join(", ", e.getValue())).append("\n");
                }
            }
            return sb.toString();
        }
    }

    /**
     * Gửi yêu cầu HTTP GET
     */
    public HttpResult sendGet(String urlString) throws Exception {
        return executeRequest(urlString, "GET", null, null);
    }

    /**
     * Gửi yêu cầu HTTP HEAD (Chỉ lấy Headers, không tải Body)
     */
    public HttpResult sendHead(String urlString) throws Exception {
        return executeRequest(urlString, "HEAD", null, null);
    }

    /**
     * Gửi yêu cầu HTTP POST kèm dữ liệu Body / Form
     */
    public HttpResult sendPost(String urlString, String postData, String contentType) throws Exception {
        return executeRequest(urlString, "POST", postData, contentType);
    }

    private HttpResult executeRequest(String urlString, String method, String postData, String contentType) throws Exception {
        URL url = new URL(urlString);
        String host = url.getHost() != null ? url.getHost().toLowerCase() : "";
        HttpURLConnection conn = (host.equals("localhost") || host.equals("127.0.0.1"))
                ? (HttpURLConnection) url.openConnection(Proxy.NO_PROXY)
                : (HttpURLConnection) url.openConnection();
        conn.setUseCaches(false);
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "Chrome/120.0 (Java-Swing-Browser; Coccoc-Like)");
        conn.setRequestProperty("Accept", "*/*");

        if ("POST".equalsIgnoreCase(method) && postData != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", contentType != null ? contentType : "application/x-www-form-urlencoded");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(postData.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        }

        HttpResult result = new HttpResult();
        result.statusCode = conn.getResponseCode();
        result.statusMessage = conn.getResponseMessage();
        result.headers.putAll(conn.getHeaderFields());

        // Nếu là HEAD, không đọc body
        if ("HEAD".equalsIgnoreCase(method)) {
            conn.disconnect();
            result.body = "(Phương thức HEAD: Không nhận nội dung Body theo chuẩn RFC 7231)";
            return result;
        }

        InputStream in = (result.statusCode >= 400) ? conn.getErrorStream() : conn.getInputStream();
        if (in != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
            result.rawBytes = baos.toByteArray();
            result.body = new String(result.rawBytes, StandardCharsets.UTF_8);
        }

        conn.disconnect();
        return result;
    }

    /**
     * Tải tệp tin từ Web Server về máy cục bộ (.pdf, .mp3, .mp4, ...)
     */
    public boolean downloadFile(String urlString, File destFile, DownloadProgressListener listener) {
        try {
            URL url = new URL(urlString);
            String host = url.getHost() != null ? url.getHost().toLowerCase() : "";
            HttpURLConnection conn = (host.equals("localhost") || host.equals("127.0.0.1"))
                    ? (HttpURLConnection) url.openConnection(Proxy.NO_PROXY)
                    : (HttpURLConnection) url.openConnection();
            conn.setUseCaches(false);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);

            long totalBytes = conn.getContentLengthLong();

            try (InputStream in = conn.getInputStream();
                 OutputStream out = new FileOutputStream(destFile)) {

                byte[] buffer = new byte[8192];
                long transferred = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    transferred += read;
                    if (listener != null) {
                        listener.onProgress(transferred, totalBytes);
                    }
                }
                out.flush();
            }
            conn.disconnect();
            return true;
        } catch (Exception e) {
            System.err.println("[Download Error] " + e.getMessage());
            return false;
        }
    }
}

