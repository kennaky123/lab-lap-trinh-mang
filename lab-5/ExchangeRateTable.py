"""Bài 1: bảng tỷ giá yêu cầu cập nhật mỗi giây."""
import queue
import threading
import time
import tkinter as tk
from tkinter import ttk, messagebox

from network import request


class ExchangeRateTable:
    def __init__(self, root):
        self.root = root
        self.stop = None
        self.generation = 0
        self.events = queue.Queue()
        root.title("Lab 5 • ExchangeRateTable")
        root.geometry("750x420")
        root.minsize(650, 380)
        main = ttk.Frame(root, padding=24)
        main.pack(fill="both", expand=True)
        ttk.Label(main, text="BẢNG TỶ GIÁ", font=("Segoe UI", 22, "bold")).pack(anchor="w")
        ttk.Label(main, text="Dữ liệu ngẫu nhiên phục vụ bài thực hành • Cập nhật mỗi giây").pack(anchor="w", pady=(4, 18))
        row = ttk.Frame(main)
        row.pack(fill="x")
        self.host = tk.StringVar(value="127.0.0.1")
        self.port = tk.StringVar(value="2345")
        ttk.Label(row, text="Máy chủ:").pack(side="left")
        ttk.Entry(row, textvariable=self.host, width=22).pack(side="left", padx=8)
        ttk.Label(row, text="Cổng:").pack(side="left")
        ttk.Entry(row, textvariable=self.port, width=8).pack(side="left", padx=8)
        self.button = ttk.Button(row, text="Bắt đầu", command=self.toggle)
        self.button.pack(side="left", padx=8)
        self.table = ttk.Treeview(main, columns=("market", "pair", "value"), show="headings", height=5)
        for key, title in [("market", "Thị trường"), ("pair", "Cặp tiền"), ("value", "Tỷ giá")]:
            self.table.heading(key, text=title)
            self.table.column(key, anchor="center", width=180)
        self.table.pack(fill="both", expand=True, pady=18)
        self.status = tk.StringVar(value="Chưa cập nhật. Khởi động ExchangeRateServer.py rồi bấm Bắt đầu.")
        ttk.Label(main, textvariable=self.status, wraplength=670).pack(anchor="w")
        root.protocol("WM_DELETE_WINDOW", self.close)
        root.after(100, self.tick)

    def toggle(self):
        if self.stop is not None:
            self.stop.set()
            self.stop = None
            self.generation += 1
            self.button.configure(text="Bắt đầu")
            self.status.set("Đã dừng cập nhật; bảng đang hiển thị dữ liệu cũ.")
            return
        try:
            host, port = self.host.get().strip(), int(self.port.get())
            if not host or not 1 <= port <= 65535:
                raise ValueError()
        except ValueError:
            messagebox.showerror("Lỗi", "Nhập IP và cổng hợp lệ.")
            return
        self.stop = threading.Event()
        self.generation += 1
        self.button.configure(text="Dừng")
        self.status.set("Đang yêu cầu dữ liệu…")
        threading.Thread(target=self.poll, args=(host, port, self.stop, self.generation), daemon=True).start()

    def poll(self, host, port, stop, generation):
        while not stop.is_set():
            start = time.monotonic()
            try:
                result = request(host, port, {"action": "rates"})
            except (OSError, ValueError) as exc:
                result = str(exc)
            self.events.put((generation, result))
            # Một yêu cầu tại một thời điểm; không chồng yêu cầu nếu mạng chậm.
            stop.wait(max(0, 1 - (time.monotonic() - start)))

    def tick(self):
        try:
            while True:
                generation, result = self.events.get_nowait()
                if generation != self.generation:
                    continue
                if isinstance(result, str):
                    self.status.set(f"Mất kết nối — dữ liệu cũ. Đang thử lại: {result}")
                else:
                    self.table.delete(*self.table.get_children())
                    for rate in result["rates"]:
                        self.table.insert("", "end", values=(rate["market"], rate["pair"], f"{rate['value']:.4f}"))
                    self.status.set("Ngày giờ máy chủ: " + result["timestamp"])
        except queue.Empty:
            pass
        self.root.after(100, self.tick)

    def close(self):
        if self.stop:
            self.stop.set()
        self.root.destroy()


if __name__ == "__main__":
    root = tk.Tk()
    ExchangeRateTable(root)
    root.mainloop()
