package rmi.common;

import java.io.Serializable;

/**
 * Đối tượng Tài khoản & Phân quyền người dùng trên hệ thống RMI
 */
public class RmiUser implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
    private String role; // "ADMIN", "EDITOR", "VIEWER"
    private boolean canAdd;
    private boolean canUpdate;
    private boolean canDelete;

    public RmiUser() {}

    public RmiUser(String username, String password, String role, boolean canAdd, boolean canUpdate, boolean canDelete) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.canAdd = canAdd;
        this.canUpdate = canUpdate;
        this.canDelete = canDelete;
    }

    public static RmiUser createByRole(String username, String password, String role) {
        String r = role != null ? role.toUpperCase().trim() : "VIEWER";
        switch (r) {
            case "ADMIN":
                return new RmiUser(username, password, "ADMIN", true, true, true);
            case "EDITOR":
                return new RmiUser(username, password, "EDITOR", true, true, false);
            default:
                return new RmiUser(username, password, "VIEWER", false, false, false);
        }
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isCanAdd() { return canAdd; }
    public void setCanAdd(boolean canAdd) { this.canAdd = canAdd; }

    public boolean isCanUpdate() { return canUpdate; }
    public void setCanUpdate(boolean canUpdate) { this.canUpdate = canUpdate; }

    public boolean isCanDelete() { return canDelete; }
    public void setCanDelete(boolean canDelete) { this.canDelete = canDelete; }

    public String getPermissionSummary() {
        StringBuilder sb = new StringBuilder("Xem");
        if (canAdd) sb.append(", Thêm");
        if (canUpdate) sb.append(", Sửa");
        if (canDelete) sb.append(", Xóa");
        return sb.toString();
    }

    @Override
    public String toString() {
        return username + " [" + role + " - " + getPermissionSummary() + "]";
    }
}

