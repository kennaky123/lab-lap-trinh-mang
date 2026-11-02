import queue
import threading
import tkinter as tk
from tkinter import ttk, messagebox
from tkinter.scrolledtext import ScrolledText

from network import request


class MailClient:
    def __init__(self, root):
        self.root = root
        self.token = None
        self.endpoint = None
        self.busy = False
        self.events = queue.Queue()
        root.title("Lab 5 • Mail Client")
        root.geometry("1000x760")
        root.minsize(850, 650)
        main = ttk.Frame(root, padding=20)
        main.pack(fill="both", expand=True)
        ttk.Label(main, text="MAIL NỘI BỘ", font=("Segoe UI", 22, "bold")).pack(anchor="w")
        ttk.Label(main, text="Tạo account • Đọc hộp thư • Gửi email đến account trên máy chủ").pack(anchor="w", pady=(4, 14))
        login = ttk.Frame(main)
        login.pack(fill="x")
        self.host = tk.StringVar(value="127.0.0.1")
        self.port = tk.StringVar(value="2525")
        self.username = tk.StringVar()
        self.password = tk.StringVar()
        self.login_entries = []
        for column, (label, variable, width) in enumerate([
            ("IP máy chủ", self.host, 19), ("Cổng", self.port, 7),
            ("Account", self.username, 18), ("Mật khẩu", self.password, 18),
        ]):
            frame = ttk.Frame(login)
            frame.grid(row=0, column=column, padx=(0, 10))
            ttk.Label(frame, text=label).pack(anchor="w")
            entry = ttk.Entry(frame, textvariable=variable, width=width, show="*" if variable is self.password else "")
            entry.pack(pady=(4, 0))
            self.login_entries.append(entry)
        controls = ttk.Frame(main)
        controls.pack(fill="x", pady=10)
        self.register_button = ttk.Button(controls, text="Tạo account", command=lambda: self.authenticate("register"))
        self.register_button.pack(side="left")
        self.login_button = ttk.Button(controls, text="Đăng nhập", command=lambda: self.authenticate("login"))
        self.login_button.pack(side="left", padx=8)
        self.logout_button = ttk.Button(controls, text="Đăng xuất", command=self.logout)
        self.logout_button.pack(side="left")
        self.refresh_button = ttk.Button(controls, text="Làm mới hộp thư", command=lambda: self.call("list"))
        self.refresh_button.pack(side="right")
        self.status = tk.StringVar(value="Chưa đăng nhập. Account gồm 3–30 chữ cái, số hoặc dấu gạch dưới.")
        ttk.Label(main, textvariable=self.status, wraplength=920).pack(anchor="w", pady=(0, 10))
        panes = ttk.Panedwindow(main, orient="horizontal")
        panes.pack(fill="both", expand=True)
        left, right = ttk.Frame(panes), ttk.Frame(panes)
        panes.add(left, weight=1)
        panes.add(right, weight=3)
        ttk.Label(left, text="TÊN FILE TRONG HỘP THƯ").pack(anchor="w")
        self.files = tk.Listbox(left, width=34, font=("Segoe UI", 10), exportselection=False)
        self.files.pack(fill="both", expand=True, pady=6, padx=(0, 10))
        ttk.Button(left, text="Đọc thư đã chọn", command=self.read).pack(anchor="w")
        self.files.bind("<Double-Button-1>", lambda _: self.read())
        ttk.Label(right, text="NỘI DUNG THƯ").pack(anchor="w")
        self.reader = ScrolledText(right, height=10, state="disabled", wrap="word", font=("Segoe UI", 10))
        self.reader.pack(fill="both", expand=True, pady=6)
        compose = ttk.LabelFrame(main, text="Soạn email", padding=10)
        compose.pack(fill="x", pady=(14, 0))
        self.recipient, self.subject = tk.StringVar(), tk.StringVar()
        ttk.Label(compose, text="Account nhận:").grid(row=0, column=0, sticky="w")
        ttk.Entry(compose, textvariable=self.recipient, width=24).grid(row=0, column=1, sticky="ew", padx=8)
        ttk.Label(compose, text="Tiêu đề:").grid(row=0, column=2)
        ttk.Entry(compose, textvariable=self.subject).grid(row=0, column=3, sticky="ew", padx=8)
        compose.columnconfigure(3, weight=1)
        self.body = ScrolledText(compose, height=5, wrap="word", font=("Segoe UI", 10))
        self.body.grid(row=1, column=0, columnspan=4, sticky="ew", pady=8)
        self.send_button = ttk.Button(compose, text="Gửi email", command=self.send_mail)
        self.send_button.grid(row=2, column=3, sticky="e")
        self.update_controls()
        root.after(100, self.tick)

    def update_controls(self):
        logged_in = self.token is not None
        for button in (self.login_button, self.register_button):
            button.configure(state="disabled" if self.busy or logged_in else "normal")
        for button in (self.logout_button, self.refresh_button, self.send_button):
            button.configure(state="normal" if logged_in and not self.busy else "disabled")
        for entry in self.login_entries:
            entry.configure(state="disabled" if logged_in or self.busy else "normal")

    def authenticate(self, action):
        self.call(action, username=self.username.get().strip(), password=self.password.get())

    def call(self, action, **data):
        if self.busy:
            return
        if action not in ("login", "register") and not self.token:
            messagebox.showinfo("Chưa đăng nhập", "Hãy đăng nhập trước.")
            return
        try:
            endpoint = self.endpoint if self.token else (self.host.get().strip(), int(self.port.get()))
            if not endpoint[0] or not 1 <= endpoint[1] <= 65535:
                raise ValueError()
        except ValueError:
            messagebox.showerror("Lỗi", "Nhập IP và cổng hợp lệ.")
            return
        payload = dict(data, action=action, token=self.token)
        self.busy = True
        self.update_controls()
        self.status.set("Đang xử lý…")

        def worker():
            try:
                result = request(*endpoint, payload)
            except (OSError, ValueError) as exc:
                result = str(exc)
            self.events.put((action, endpoint, result))
        threading.Thread(target=worker, daemon=True).start()

    def logout(self):
        self.call("logout")

    def read(self):
        selected = self.files.curselection()
        if selected:
            self.call("read", filename=self.files.get(selected[0]))

    def send_mail(self):
        self.call("send", to=self.recipient.get().strip(), subject=self.subject.get(), body=self.body.get("1.0", "end-1c"))

    def clear_session(self):
        self.token = None
        self.endpoint = None
        self.password.set("")
        self.files.delete(0, "end")
        self.reader.configure(state="normal")
        self.reader.delete("1.0", "end")
        self.reader.configure(state="disabled")

    def tick(self):
        try:
            action, endpoint, result = self.events.get_nowait()
            self.busy = False
            if isinstance(result, str):
                self.status.set("Lỗi: " + result)
                # Luôn cho phép thoát phiên cục bộ nếu máy chủ đã ngừng chạy.
                if action == "logout":
                    self.clear_session()
                messagebox.showerror("Yêu cầu thất bại", result)
            else:
                if action == "login":
                    self.token, self.endpoint = result["token"], endpoint
                    self.password.set("")
                    self.status.set("Đã đăng nhập: " + result["username"])
                elif action == "logout":
                    self.clear_session()
                    self.status.set("Đã đăng xuất")
                elif action == "read":
                    self.reader.configure(state="normal")
                    self.reader.delete("1.0", "end")
                    self.reader.insert("1.0", result["content"])
                    self.reader.configure(state="disabled")
                    self.status.set("Đang đọc: " + result["filename"])
                else:
                    self.status.set(result.get("message", "Đã cập nhật hộp thư"))
                if "files" in result:
                    self.files.delete(0, "end")
                    for filename in result["files"]:
                        self.files.insert("end", filename)
                if action == "send":
                    self.body.delete("1.0", "end")
                    self.subject.set("")
            self.update_controls()
        except queue.Empty:
            pass
        self.root.after(100, self.tick)


if __name__ == "__main__":
    root = tk.Tk()
    MailClient(root)
    root.mainloop()
