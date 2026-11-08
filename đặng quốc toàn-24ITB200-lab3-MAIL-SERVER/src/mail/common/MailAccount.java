package mail.common;

import java.io.Serializable;

/**
 * Đại diện tài khoản hòm thư điện tử trên Mail Server
 */
public class MailAccount implements Serializable {
    private static final long serialVersionUID = 1L;

    private String email;
    private String password;
    private String displayName;
    private boolean isEnabled;
    private long createdAt;

    public MailAccount(String email, String password, String displayName) {
        this.email = email.toLowerCase().trim();
        this.password = password;
        this.displayName = displayName;
        this.isEnabled = true;
        this.createdAt = System.currentTimeMillis();
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email.toLowerCase().trim(); }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public boolean isEnabled() { return isEnabled; }
    public void setEnabled(boolean enabled) { isEnabled = enabled; }

    public long getCreatedAt() { return createdAt; }
}

