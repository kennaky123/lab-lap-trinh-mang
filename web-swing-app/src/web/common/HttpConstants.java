package web.common;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Các hằng số giao thức HTTP/1.1 và bảng ánh xạ MIME types
 */
public class HttpConstants {
    public static final int DEFAULT_PORT = 8080;
    public static final String WEB_ROOT = resolvePath("web_root");
    public static final String DOWNLOADS_DIR = resolvePath("downloads");
    public static final String DATA_DIR = resolvePath("browser_data");

    private static String resolvePath(String subDir) {
        File local = new File(subDir);
        if ("web_root".equals(subDir)) {
            if (new File(local, "index.html").exists()) {
                return local.getPath();
            }
            File nested = new File("lap-trinh-mang/web-swing-app/web_root");
            if (new File(nested, "index.html").exists()) {
                return nested.getPath();
            }
        } else {
            File nestedParent = new File("lap-trinh-mang/web-swing-app");
            if (!local.exists() && nestedParent.exists()) {
                return new File(nestedParent, subDir).getPath();
            }
        }
        return local.getPath();
    }

    // Bảng tra cứu MIME type phục vụ đa dịch vụ (.html, .pdf, .mp3, .mp4...)
    private static final Map<String, String> MIME_TYPES = new HashMap<>();

    static {
        MIME_TYPES.put("html", "text/html; charset=UTF-8");
        MIME_TYPES.put("htm", "text/html; charset=UTF-8");
        MIME_TYPES.put("css", "text/css; charset=UTF-8");
        MIME_TYPES.put("js", "application/javascript");
        MIME_TYPES.put("json", "application/json; charset=UTF-8");
        MIME_TYPES.put("txt", "text/plain; charset=UTF-8");

        // Dịch vụ Đa phương tiện
        MIME_TYPES.put("pdf", "application/pdf");
        MIME_TYPES.put("mp3", "audio/mpeg");
        MIME_TYPES.put("mp4", "video/mp4");
        MIME_TYPES.put("wav", "audio/wav");
        MIME_TYPES.put("ogg", "audio/ogg");

        // Hình ảnh
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("ico", "image/x-icon");
    }

    public static String getMimeType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        int dot = fileName.lastIndexOf('.');
        if (dot != -1) {
            String ext = fileName.substring(dot + 1).toLowerCase();
            return MIME_TYPES.getOrDefault(ext, "application/octet-stream");
        }
        return "application/octet-stream";
    }

    public static String getStatusText(int code) {
        switch (code) {
            case 200: return "OK";
            case 206: return "Partial Content";
            case 301: return "Moved Permanently";
            case 302: return "Found";
            case 400: return "Bad Request";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 500: return "Internal Server Error";
            default: return "Unknown";
        }
    }
}

