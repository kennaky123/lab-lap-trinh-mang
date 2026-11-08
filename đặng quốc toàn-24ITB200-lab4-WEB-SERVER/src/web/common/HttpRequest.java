package web.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Phân tích và biểu diễn gói tin yêu cầu HTTP Request (GET, HEAD, POST)
 */
public class HttpRequest {
    private String method;
    private String uri;
    private String protocol;
    private final Map<String, String> headers = new HashMap<>();
    private String body = "";
    private final Map<String, String> formParams = new HashMap<>();

    public static HttpRequest parse(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.trim().isEmpty()) {
            return null;
        }

        HttpRequest req = new HttpRequest();
        String[] parts = requestLine.split("\\s+");
        if (parts.length >= 3) {
            req.method = parts[0].toUpperCase();
            req.uri = parts[1];
            req.protocol = parts[2];
        } else {
            return null;
        }

        // Đọc các dòng Headers
        String line;
        int contentLength = 0;
        while ((line = reader.readLine()) != null && !line.trim().isEmpty()) {
            int colon = line.indexOf(':');
            if (colon != -1) {
                String key = line.substring(0, colon).trim().toLowerCase();
                String val = line.substring(colon + 1).trim();
                req.headers.put(key, val);
                if ("content-length".equals(key)) {
                    try {
                        contentLength = Integer.parseInt(val);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // Đọc phần Body (đối với phương thức POST)
        if ("POST".equalsIgnoreCase(req.method) && contentLength > 0) {
            char[] bodyChars = new char[contentLength];
            int read = 0;
            while (read < contentLength) {
                int r = reader.read(bodyChars, read, contentLength - read);
                if (r == -1) break;
                read += r;
            }
            req.body = new String(bodyChars, 0, read);

            // Phân tích dữ liệu Form URL Encoded nếu có
            String contentType = req.getHeader("content-type");
            if (contentType != null && contentType.contains("application/x-www-form-urlencoded")) {
                req.parseFormUrlEncoded(req.body);
            }
        }

        return req;
    }

    private void parseFormUrlEncoded(String data) {
        String[] pairs = data.split("&");
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq != -1) {
                try {
                    String k = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8.name());
                    String v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8.name());
                    formParams.put(k, v);
                } catch (Exception ignored) {}
            }
        }
    }

    public String getMethod() { return method; }
    public String getUri() { return uri; }
    public String getProtocol() { return protocol; }
    public String getHeader(String name) { return headers.get(name.toLowerCase()); }
    public Map<String, String> getHeaders() { return headers; }
    public String getBody() { return body; }
    public Map<String, String> getFormParams() { return formParams; }

    public String getCleanPath() {
        int q = uri.indexOf('?');
        return q != -1 ? uri.substring(0, q) : uri;
    }
}

