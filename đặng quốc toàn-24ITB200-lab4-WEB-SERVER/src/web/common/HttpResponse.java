package web.common;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Tạo và gửi gói tin phản hồi HTTP Response chuẩn HTTP/1.1
 */
public class HttpResponse {
    private int statusCode = 200;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private byte[] bodyData;

    public HttpResponse(int statusCode) {
        this.statusCode = statusCode;
        setHeader("Server", "Java-Swing-HttpServer/1.1");
        setHeader("Date", getServerTime());
        setHeader("Connection", "close");
    }

    public void setHeader(String name, String value) {
        headers.put(name, value);
    }

    public void setContentType(String mimeType) {
        setHeader("Content-Type", mimeType);
    }

    public void setBody(String text) {
        this.bodyData = text.getBytes(StandardCharsets.UTF_8);
        setHeader("Content-Length", String.valueOf(bodyData.length));
    }

    public void setBody(byte[] data) {
        this.bodyData = data;
        setHeader("Content-Length", String.valueOf(data != null ? data.length : 0));
    }

    public void writeTo(OutputStream out, boolean sendBody) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(statusCode).append(" ").append(HttpConstants.getStatusText(statusCode)).append("\r\n");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }
        sb.append("\r\n");

        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        if (sendBody && bodyData != null && bodyData.length > 0) {
            out.write(bodyData);
        }
        out.flush();
    }

    public static HttpResponse createHtml(int statusCode, String htmlContent) {
        HttpResponse resp = new HttpResponse(statusCode);
        resp.setContentType("text/html; charset=UTF-8");
        resp.setBody(htmlContent);
        return resp;
    }

    public static HttpResponse create404(String uri) {
        String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>404 Not Found</title></head>"
                + "<body style='font-family: Arial; text-align: center; padding: 40px;'>"
                + "<h1 style='color: #d93025;'>404 - Không Tìm Thấy Tài Nguyên</h1>"
                + "<p>Tài nguyên <code>" + uri + "</code> không tồn tại trên Web Server.</p>"
                + "<hr style='width: 50%; margin: 20px auto;'><a href='/'>Quay về Trang Chủ</a></body></html>";
        return createHtml(404, html);
    }

    private String getServerTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date());
    }

    public int getStatusCode() { return statusCode; }
    public Map<String, String> getHeaders() { return headers; }
    public byte[] getBodyData() { return bodyData; }
}

