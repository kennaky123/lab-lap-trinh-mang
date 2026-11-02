import queue
import threading
import tkinter as tk
from datetime import datetime
from tkinter import filedialog, messagebox, ttk
from tkinter.scrolledtext import ScrolledText

from peer import Peer


class ChatApp:
    def __init__(self, root):
        self.root = root
        self.peer = None
        self.peers = []
        self.history = {}
        self.unread = {}
        self.selected = None
        self.inbox = queue.Queue()
        self.closed = False
        root.title("LAN Link • Chat & truyền file P2P")
        root.geometry("1000x700")
        root.minsize(820, 560)
        root.configure(bg="#eef2f8")
        style = ttk.Style()
        style.theme_use("clam")
        style.configure("TFrame", background="#eef2f8")
        style.configure("TLabel", background="#eef2f8", font=("Segoe UI", 10))
        style.configure("TButton", font=("Segoe UI", 10), padding=7)
        style.configure("Title.TLabel", font=("Segoe UI", 21, "bold"), foreground="#16365a")
        main = ttk.Frame(root, padding=20)
        main.pack(fill="both", expand=True)
        ttk.Label(main, text="LAN Link", style="Title.TLabel").pack(anchor="w")
        ttk.Label(main, text="Chat riêng & truyền file trực tiếp giữa các máy trong mạng LAN").pack(anchor="w", pady=(0, 15))
        login = ttk.Frame(main)
        login.pack(fill="x")
        self.name = tk.StringVar(value="")
        self.host = tk.StringVar(value="127.0.0.1")
        self.port = tk.StringVar(value="9000")
        self.listen = tk.StringVar(value="0")
        for column, (label, variable, width) in enumerate([
            ("Tên của bạn", self.name, 18), ("IP máy chủ", self.host, 19),
            ("Cổng máy chủ", self.port, 11), ("Cổng P2P (0 = tự chọn)", self.listen, 20),
        ]):
            field = ttk.Frame(login)
            field.grid(row=0, column=column, padx=(0, 10), sticky="w")
            ttk.Label(field, text=label).pack(anchor="w")
            ttk.Entry(field, textvariable=variable, width=width).pack(pady=(5, 0))
        self.connect_button = ttk.Button(login, text="Kết nối", command=self.connect)
        self.connect_button.grid(row=0, column=4, sticky="s")
        self.status = tk.StringVar(value="Chưa kết nối • Khởi động server.py trước khi đăng nhập")
        ttk.Label(main, textvariable=self.status, foreground="#43678d").pack(anchor="w", pady=12)
        body = ttk.Frame(main)
        body.pack(fill="both", expand=True)
        sidebar = ttk.Frame(body, width=210)
        sidebar.pack(side="left", fill="y", padx=(0, 16))
        ttk.Label(sidebar, text="NGƯỜI ĐANG ONLINE").pack(anchor="w", pady=(0, 8))
        self.users = tk.Listbox(sidebar, width=25, font=("Segoe UI", 11),
                                bg="white", fg="#16365a", selectbackground="#2563eb",
                                relief="flat", borderwidth=0, exportselection=False)
        self.users.pack(fill="both", expand=True)
        self.users.bind("<<ListboxSelect>>", self.select)
        chat = ttk.Frame(body)
        chat.pack(side="left", fill="both", expand=True)
        self.heading = tk.StringVar(value="Chọn một người để trò chuyện")
        ttk.Label(chat, textvariable=self.heading, font=("Segoe UI", 12, "bold")).pack(anchor="w", pady=(0, 8))
        self.messages = ScrolledText(chat, state="disabled", wrap="word", font=("Segoe UI", 11),
                                     bg="white", fg="#23354d", relief="flat", padx=14, pady=14)
        self.messages.pack(fill="both", expand=True)
        compose = ttk.Frame(chat)
        compose.pack(fill="x", pady=(10, 0))
        self.text = ttk.Entry(compose, font=("Segoe UI", 11))
        self.text.pack(side="left", fill="x", expand=True)
        self.text.bind("<Return>", lambda _: self.send())
        ttk.Button(compose, text="Gửi tin", command=self.send).pack(side="left", padx=6)
        ttk.Button(compose, text="Gửi file…", command=self.send_file).pack(side="left")
        self.transfer_text = tk.StringVar(value="File được chia khối 64 KiB • Kiểm tra toàn vẹn SHA-256")
        ttk.Label(main, textvariable=self.transfer_text).pack(anchor="w", pady=(14, 4))
        self.progress = ttk.Progressbar(main, maximum=100)
        self.progress.pack(fill="x")
        self.log = ScrolledText(main, height=4, state="disabled", font=("Segoe UI", 9), relief="flat")
        self.log.pack(fill="x", pady=(8, 0))
        root.protocol("WM_DELETE_WINDOW", self.close)
        root.after(100, self.tick)

    def connect(self):
        if self.peer:
            self.peer.close()
            self.peer = None
            self.peers = []
            self.update_users()
            self.connect_button.configure(text="Kết nối")
            self.status.set("Đã ngắt kết nối")
            return
        try:
            name, host = self.name.get().strip(), self.host.get().strip()
            port, listen = int(self.port.get()), int(self.listen.get())
            if not 1 <= len(name) <= 30 or not host or not 1 <= port <= 65535 or not 0 <= listen <= 65535:
                raise ValueError()
        except ValueError:
            messagebox.showerror("Thông tin chưa hợp lệ", "Nhập tên 1–30 ký tự, IP máy chủ và cổng hợp lệ.")
            return
        self.connect_button.configure(state="disabled")
        self.status.set("Đang kết nối máy chủ…")

        def worker():
            try:
                peer = Peer(name, host, port, listen)
                if self.closed:
                    peer.close()
                else:
                    self.inbox.put((peer, None))
            except Exception as exc:
                self.inbox.put((None, str(exc)))
        threading.Thread(target=worker, daemon=True).start()

    def update_users(self):
        self.users.delete(0, "end")
        for index, peer in enumerate(self.peers):
            count = self.unread.get(peer["id"], 0)
            self.users.insert("end", peer["name"] + (f" ({count} mới)" if count else ""))
            if peer["id"] == self.selected:
                self.users.selection_set(index)

    def select(self, _=None):
        indexes = self.users.curselection()
        if indexes:
            peer = self.peers[indexes[0]]
            self.selected = peer["id"]
            self.unread[self.selected] = 0
            self.heading.set(f"{peer['name']} • {peer['host']}:{peer['port']}")
            self.update_users()
            self.render_history()

    def render_history(self):
        self.messages.configure(state="normal")
        self.messages.delete("1.0", "end")
        self.messages.insert("end", "\n\n".join(self.history.get(self.selected, [])))
        self.messages.configure(state="disabled")
        self.messages.see("end")

    def target(self):
        if self.peer and not self.peer.stop.is_set():
            for peer in self.peers:
                if peer["id"] == self.selected:
                    return peer
        messagebox.showinfo("Chọn người nhận", "Kết nối máy chủ và chọn một người đang online.")
        return None

    def send(self):
        message = self.text.get().strip()
        if not message:
            return
        target = self.target()
        if target:
            try:
                self.peer.send_chat(target, message)
                self.text.delete(0, "end")
            except ValueError as exc:
                messagebox.showerror("Không gửi được", str(exc))

    def send_file(self):
        target = self.target()
        if target:
            path = filedialog.askopenfilename(title="Chọn file cần gửi (tối đa 2 GiB)")
            if path:
                self.peer.send_file(target, path)

    def write_log(self, message):
        self.log.configure(state="normal")
        self.log.insert("end", f"[{datetime.now():%H:%M:%S}] {message}\n")
        self.log.configure(state="disabled")
        self.log.see("end")

    def handle(self, event):
        kind = event["kind"]
        if kind == "peers":
            self.peers = event["peers"]
            self.update_users()
        elif kind in ("chat", "sent"):
            peer_id = event["peer_id"]
            author = "Bạn" if kind == "sent" else event["name"]
            self.history.setdefault(peer_id, []).append(f"{author} · {datetime.now():%H:%M}\n{event['text']}")
            if peer_id == self.selected:
                self.render_history()
            elif kind == "chat":
                self.unread[peer_id] = self.unread.get(peer_id, 0) + 1
                self.update_users()
                self.write_log(f"Tin nhắn mới từ {event['name']}")
        elif kind == "offer":
            path = None
            if messagebox.askyesno("Nhận file P2P", f"{event['name']} muốn gửi:\n{event['filename']}\n{event['size']:,} byte\n\nBạn có muốn nhận? (90 giây để chọn nơi lưu)"):
                path = filedialog.asksaveasfilename(title="Lưu file nhận được", initialfile=event["filename"])
            if self.peer:
                self.peer.decide(event["transfer_id"], path or None)
        elif kind == "progress":
            self.progress["value"] = event["percent"]
            self.transfer_text.set(f"{event['filename']} • {event['percent']:.0f}% • tác vụ {event['transfer_id'][:6]}")
        else:
            self.write_log(event["message"])
            if kind in ("done", "error"):
                self.transfer_text.set(event["message"])
                self.progress["value"] = 100 if kind == "done" else 0
            if kind == "offline":
                self.status.set(event["message"])
                self.peer = None
                self.peers = []
                self.update_users()
                self.connect_button.configure(text="Kết nối")

    def tick(self):
        try:
            peer, error = self.inbox.get_nowait()
            self.connect_button.configure(state="normal")
            if error:
                self.status.set("Kết nối thất bại")
                messagebox.showerror("Lỗi kết nối", error)
            else:
                self.peer = peer
                self.connect_button.configure(text="Ngắt kết nối")
                self.status.set(f"Đã kết nối: {peer.name} • Cổng P2P: {peer.listener.getsockname()[1]}")
        except queue.Empty:
            pass
        for _ in range(100):
            if not self.peer:
                break
            try:
                self.handle(self.peer.events.get_nowait())
            except queue.Empty:
                break
        self.root.after(100, self.tick)

    def close(self):
        self.closed = True
        if self.peer:
            self.peer.close()
        self.root.destroy()


if __name__ == "__main__":
    window = tk.Tk()
    ChatApp(window)
    window.mainloop()
