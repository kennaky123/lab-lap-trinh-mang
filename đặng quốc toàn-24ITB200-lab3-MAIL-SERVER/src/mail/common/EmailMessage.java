package mail.common;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đại diện một bức thư điện tử (Email Message) theo chuẩn RFC 5322
 */
public class EmailMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String from;
    private String to;
    private String subject;
    private String body;
    private long timestamp;
    private boolean isRead;
    private boolean isDeleted;

    private String attachmentName;
    private byte[] attachmentData;

    public EmailMessage() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.isRead = false;
        this.isDeleted = false;
    }

    public EmailMessage(String from, String to, String subject, String body) {
        this.id = UUID.randomUUID().toString();
        this.from = from;
        this.to = to;
        this.subject = subject;
        this.body = body;
        this.timestamp = System.currentTimeMillis();
        this.isRead = false;
        this.isDeleted = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public String getAttachmentName() { return attachmentName; }
    public void setAttachmentName(String attachmentName) { this.attachmentName = attachmentName; }

    public byte[] getAttachmentData() { return attachmentData; }
    public void setAttachmentData(byte[] attachmentData) { this.attachmentData = attachmentData; }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        return sdf.format(new Date(timestamp));
    }

    public long getApproximateSize() {
        long size = (subject != null ? subject.length() : 0) + (body != null ? body.length() : 0) + 128;
        if (attachmentData != null) {
            size += attachmentData.length;
        }
        return size;
    }

    public int getRfc822ByteSize() {
        return toRfc822String().getBytes(StandardCharsets.UTF_8).length;
    }

    public String toRfc822String() {
        SimpleDateFormat rfcDate = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
        StringBuilder sb = new StringBuilder();
        sb.append("From: ").append(from != null ? from : "").append("\r\n");
        sb.append("To: ").append(to != null ? to : "").append("\r\n");
        sb.append("Date: ").append(rfcDate.format(new Date(timestamp))).append("\r\n");
        sb.append("Subject: ").append(encodeMimeHeader(subject != null ? subject : "")).append("\r\n");
        sb.append("Message-ID: <").append(id).append("@mailserver.local>\r\n");
        sb.append("MIME-Version: 1.0\r\n");

        if (attachmentName != null && attachmentData != null) {
            String boundary = "----=_Part_" + id;
            String encodedAttachName = encodeMimeHeader(attachmentName);
            sb.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n");

            sb.append("--").append(boundary).append("\r\n");
            sb.append("Content-Type: text/plain; charset=UTF-8\r\n");
            sb.append("Content-Transfer-Encoding: 8bit\r\n\r\n");
            sb.append(body != null ? body : "").append("\r\n\r\n");

            sb.append("--").append(boundary).append("\r\n");
            sb.append("Content-Type: application/octet-stream; name=\"").append(encodedAttachName).append("\"\r\n");
            sb.append("Content-Disposition: attachment; filename=\"").append(encodedAttachName).append("\"\r\n");
            sb.append("Content-Transfer-Encoding: base64\r\n\r\n");
            String b64Wrapped = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                    .encodeToString(attachmentData);
            sb.append(b64Wrapped).append("\r\n\r\n");
            sb.append("--").append(boundary).append("--");
        } else {
            sb.append("Content-Type: text/plain; charset=UTF-8\r\n");
            sb.append("Content-Transfer-Encoding: 8bit\r\n\r\n");
            sb.append(body != null ? body : "");
        }
        return sb.toString();
    }

    private static String encodeMimeHeader(String text) {
        if (text == null || text.isEmpty()) return "";
        boolean hasNonAscii = false;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 127) {
                hasNonAscii = true;
                break;
            }
        }
        if (!hasNonAscii) return text;
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    public static EmailMessage fromRfc822String(String raw, String defaultFrom, String defaultTo) {
        String from = defaultFrom != null ? defaultFrom : "Unknown";
        String to = defaultTo != null ? defaultTo : "Unknown";
        String subject = "(Không có tiêu đề)";
        String body = raw != null ? raw : "";
        String[] attachNameHolder = new String[1];
        byte[][] attachDataHolder = new byte[1][];

        if (raw != null) {
            int sepLen = 4;
            int headerEnd = raw.indexOf("\r\n\r\n");
            if (headerEnd == -1) {
                headerEnd = raw.indexOf("\n\n");
                sepLen = 2;
            }
            if (headerEnd != -1) {
                String headers = unfoldHeaders(raw.substring(0, headerEnd));
                String rawBody = raw.substring(headerEnd + sepLen);

                for (String h : headers.split("\r?\n")) {
                    String lower = h.toLowerCase();
                    if (lower.startsWith("from:")) {
                        from = decodeMimeHeader(h.substring(5).trim());
                    } else if (lower.startsWith("to:")) {
                        to = decodeMimeHeader(h.substring(3).trim());
                    } else if (lower.startsWith("subject:")) {
                        subject = decodeMimeHeader(h.substring(8).trim());
                    }
                }

                StringBuilder textBuilder = new StringBuilder();
                StringBuilder htmlBuilder = new StringBuilder();
                parseMimeEntity(headers, rawBody, textBuilder, htmlBuilder, attachNameHolder, attachDataHolder);

                if (textBuilder.length() > 0) {
                    body = textBuilder.toString();
                } else if (htmlBuilder.length() > 0) {
                    body = htmlBuilder.toString()
                            .replaceAll("(?i)<br\\s*/?>", "\n")
                            .replaceAll("(?i)</p>", "\n")
                            .replaceAll("<[^>]+>", "")
                            .replace("&nbsp;", " ")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&amp;", "&")
                            .trim();
                } else {
                    body = "";
                }
            }
        }

        EmailMessage msg = new EmailMessage(from, to, subject, body);
        msg.setAttachmentName(attachNameHolder[0]);
        msg.setAttachmentData(attachDataHolder[0]);
        return msg;
    }

    private static String unfoldHeaders(String headers) {
        return headers.replaceAll("\r?\n[ \t]+", " ");
    }

    private static void parseMimeEntity(String rawHeaders, String rawBody,
                                        StringBuilder textBuilder, StringBuilder htmlBuilder,
                                        String[] attachNameHolder, byte[][] attachDataHolder) {
        String headers = unfoldHeaders(rawHeaders);
        String contentType = getHeaderValue(headers, "Content-Type");
        String transferEncoding = getHeaderValue(headers, "Content-Transfer-Encoding");
        String contentDisposition = getHeaderValue(headers, "Content-Disposition");

        String boundary = extractParam(contentType, "boundary=");
        if (boundary != null && !boundary.isEmpty()) {
            String[] parts = rawBody.split("--" + Pattern.quote(boundary));
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i];
                if (part.startsWith("--")) break;
                if (part.startsWith("\r\n")) part = part.substring(2);
                else if (part.startsWith("\n")) part = part.substring(1);

                int sepLen = 4;
                int partHeaderEnd = part.indexOf("\r\n\r\n");
                if (partHeaderEnd == -1) {
                    partHeaderEnd = part.indexOf("\n\n");
                    sepLen = 2;
                }
                if (partHeaderEnd == -1) continue;

                String partHeaders = part.substring(0, partHeaderEnd);
                String partContent = part.substring(partHeaderEnd + sepLen);
                if (partContent.endsWith("\r\n")) partContent = partContent.substring(0, partContent.length() - 2);
                else if (partContent.endsWith("\n")) partContent = partContent.substring(0, partContent.length() - 1);

                parseMimeEntity(partHeaders, partContent, textBuilder, htmlBuilder, attachNameHolder, attachDataHolder);
            }
            return;
        }

        String ctLower = contentType.toLowerCase();
        String cdLower = contentDisposition.toLowerCase();
        String fn = extractParam(contentDisposition, "filename=");
        if (fn == null) fn = extractParam(contentType, "name=");

        if (cdLower.contains("attachment") || (fn != null && !ctLower.startsWith("text/"))) {
            if (attachNameHolder[0] == null) {
                attachNameHolder[0] = fn != null ? decodeMimeHeader(fn) : "attachment.bin";
                try {
                    if ("base64".equalsIgnoreCase(transferEncoding.trim())) {
                        String cleanB64 = rawBody.replaceAll("\\s+", "");
                        attachDataHolder[0] = Base64.getDecoder().decode(cleanB64.getBytes(StandardCharsets.US_ASCII));
                    } else {
                        attachDataHolder[0] = rawBody.getBytes(StandardCharsets.UTF_8);
                    }
                } catch (Exception ignored) {}
            }
            return;
        }

        String charsetName = extractParam(contentType, "charset=");
        Charset charset = StandardCharsets.UTF_8;
        if (charsetName != null && !charsetName.isEmpty()) {
            try {
                charset = Charset.forName(charsetName);
            } catch (Exception ignored) {}
        }

        String decoded = decodeTransferEncoding(rawBody, transferEncoding, charset);
        if (ctLower.contains("text/html")) {
            if (htmlBuilder.length() > 0) htmlBuilder.append("\n");
            htmlBuilder.append(decoded.trim());
        } else if (ctLower.isEmpty() || ctLower.contains("text/plain")) {
            if (textBuilder.length() > 0) textBuilder.append("\n");
            textBuilder.append(decoded.trim());
        }
    }

    private static String getHeaderValue(String unfoldedHeaders, String headerName) {
        String prefix = headerName.toLowerCase() + ":";
        for (String line : unfoldedHeaders.split("\r?\n")) {
            if (line.toLowerCase().startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private static String decodeTransferEncoding(String rawBody, String encoding, Charset charset) {
        if (rawBody == null) return "";
        String enc = encoding != null ? encoding.trim().toLowerCase() : "";
        if ("base64".equals(enc)) {
            try {
                byte[] bytes = Base64.getDecoder().decode(rawBody.replaceAll("\\s+", ""));
                return new String(bytes, charset);
            } catch (Exception e) {
                return rawBody;
            }
        }
        if ("quoted-printable".equals(enc)) {
            return decodeQuotedPrintable(rawBody, charset, false);
        }
        return rawBody;
    }

    private static String decodeQuotedPrintable(String input, Charset charset, boolean headerMode) {
        String cleaned = input.replaceAll("=\r?\n", "");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (headerMode && c == '_') {
                baos.write(' ');
            } else if (c == '=' && i + 2 < cleaned.length()) {
                String hex = cleaned.substring(i + 1, i + 3);
                try {
                    baos.write(Integer.parseInt(hex, 16));
                    i += 2;
                } catch (NumberFormatException e) {
                    baos.write(c);
                }
            } else {
                byte[] b = String.valueOf(c).getBytes(charset);
                baos.write(b, 0, b.length);
            }
        }
        return new String(baos.toByteArray(), charset);
    }

    private static String decodeMimeHeader(String text) {
        if (text == null || !text.contains("=?")) return text;
        try {
            // Collapse whitespace between adjacent encoded words per RFC 2047
            String normalized = text.replaceAll("\\?=\\s+=\\?", "?==?");
            Pattern pattern = Pattern.compile("=\\?([^?]+)\\?([BbQq])\\?([^?]+)\\?=");
            Matcher matcher = pattern.matcher(normalized);
            StringBuilder sb = new StringBuilder();
            int lastEnd = 0;
            while (matcher.find()) {
                sb.append(normalized, lastEnd, matcher.start());
                String charsetName = matcher.group(1);
                String encoding = matcher.group(2).toUpperCase();
                String encodedText = matcher.group(3);
                Charset cs = StandardCharsets.UTF_8;
                try {
                    cs = Charset.forName(charsetName);
                } catch (Exception ignored) {}
                if ("B".equals(encoding)) {
                    byte[] bytes = Base64.getDecoder().decode(encodedText);
                    sb.append(new String(bytes, cs));
                } else {
                    sb.append(decodeQuotedPrintable(encodedText, cs, true));
                }
                lastEnd = matcher.end();
            }
            sb.append(normalized.substring(lastEnd));
            return sb.toString();
        } catch (Exception e) {
            return text;
        }
    }

    private static String extractParam(String headers, String key) {
        if (headers == null || headers.isEmpty()) return null;
        int idx = headers.toLowerCase().indexOf(key.toLowerCase());
        if (idx == -1) return null;
        String sub = headers.substring(idx + key.length()).trim();
        if (sub.startsWith("\"")) {
            int end = sub.indexOf('"', 1);
            if (end != -1) return sub.substring(1, end);
        }
        int end = sub.indexOf(';');
        if (end == -1) end = sub.indexOf('\r');
        if (end == -1) end = sub.indexOf('\n');
        return (end != -1 ? sub.substring(0, end) : sub).trim();
    }
}
