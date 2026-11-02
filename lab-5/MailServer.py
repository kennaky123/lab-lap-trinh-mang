"""Bài 2: Mail Server nội bộ, lưu mỗi thư thành một file .txt."""
import argparse
from datetime import datetime
import hashlib
import hmac
import json
from pathlib import Path
import re
import secrets
import socketserver
import threading
import time
import uuid

from network import receive, send

WELCOME = "Thank you for using this service. we hope that you will feel comfortable using our mail service.\n"


class MailStore:
    def __init__(self, directory):
        self.root = Path(directory).resolve()
        self.boxes = self.root / "mailboxes"
        self.boxes.mkdir(parents=True, exist_ok=True)
        self.accounts_file = self.root / "accounts.json"
        self.accounts = json.loads(self.accounts_file.read_text(encoding="utf-8")) if self.accounts_file.exists() else {}
        self.lock = threading.RLock()
        self.sessions = {}

    @staticmethod
    def username(value):
        if not isinstance(value, str) or not re.fullmatch(r"[a-zA-Z0-9_]{3,30}", value):
            raise ValueError("Account gồm 3–30 chữ cái, chữ số hoặc dấu gạch dưới")
        return value.lower()

    @staticmethod
    def password(value):
        if not isinstance(value, str) or not 4 <= len(value) <= 128:
            raise ValueError("Mật khẩu dài từ 4 đến 128 ký tự")
        return value

    @staticmethod
    def digest(password, salt):
        return hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), bytes.fromhex(salt), 200000).hex()

    def filenames(self, name):
        return sorted(p.name for p in (self.boxes / name).iterdir() if p.is_file() and p.suffix == ".txt")

    def execute(self, data):
        with self.lock:
            return self._execute(data)

    def _execute(self, data):
        action = data.get("action")
        if action in ("register", "login"):
            name = self.username(data.get("username"))
            password = self.password(data.get("password"))
            if action == "register":
                if name in self.accounts:
                    raise ValueError("Account đã tồn tại")
                salt = secrets.token_hex(16)
                credentials = {"salt": salt, "hash": self.digest(password, salt)}
                mailbox = self.boxes / name
                mailbox.mkdir()
                try:
                    (mailbox / "new_email.txt").write_text(WELCOME, encoding="utf-8")
                    updated = dict(self.accounts, **{name: credentials})
                    temporary = self.accounts_file.with_suffix(".tmp")
                    temporary.write_text(json.dumps(updated, indent=2), encoding="utf-8")
                    temporary.replace(self.accounts_file)
                    self.accounts = updated
                except OSError:
                    (mailbox / "new_email.txt").unlink(missing_ok=True)
                    mailbox.rmdir()
                    raise
                return {"ok": True, "message": f"Đã tạo account {name} và new_email.txt"}
            account = self.accounts.get(name)
            if not account or not hmac.compare_digest(account["hash"], self.digest(password, account["salt"])):
                raise ValueError("Sai account hoặc mật khẩu")
            now = time.monotonic()
            self.sessions = {key: value for key, value in self.sessions.items() if value[1] > now}
            token = secrets.token_urlsafe(32)
            self.sessions[token] = (name, now + 12 * 3600)
            # Theo đề bài: trả tất cả tên file ngay trong phản hồi đăng nhập.
            return {"ok": True, "token": token, "username": name, "files": self.filenames(name)}

        token = data.get("token")
        session = self.sessions.get(token) if isinstance(token, str) else None
        if not session or session[1] <= time.monotonic():
            raise ValueError("Phiên đăng nhập hết hạn hoặc không hợp lệ. Hãy đăng nhập lại.")
        name = session[0]
        if action == "logout":
            self.sessions.pop(token, None)
            return {"ok": True}
        if action == "list":
            return {"ok": True, "files": self.filenames(name)}
        if action == "read":
            filename = data.get("filename")
            if not isinstance(filename, str) or filename not in self.filenames(name):
                raise ValueError("Không tìm thấy thư trong hộp thư của bạn")
            content = (self.boxes / name / filename).read_text(encoding="utf-8")
            return {"ok": True, "filename": filename, "content": content}
        if action == "send":
            recipient = self.username(data.get("to"))
            if recipient not in self.accounts:
                raise ValueError("Account người nhận không tồn tại")
            subject, body = data.get("subject", ""), data.get("body", "")
            if not isinstance(subject, str) or len(subject) > 200 or "\n" in subject or "\r" in subject:
                raise ValueError("Tiêu đề tối đa 200 ký tự, không xuống dòng")
            if not isinstance(body, str) or not body.strip() or len(body.encode("utf-8")) > 256 * 1024:
                raise ValueError("Nội dung thư không được rỗng và tối đa 256 KiB UTF-8")
            now = datetime.now().astimezone()
            filename = now.strftime("%Y%m%d_%H%M%S_%f_") + uuid.uuid4().hex[:12] + ".txt"
            content = f"From: {name}\nTo: {recipient}\nSubject: {subject}\nDate: {now.isoformat(timespec='seconds')}\n\n{body}\n"
            target = self.boxes / recipient / filename
            temporary = target.with_suffix(".tmp")
            try:
                temporary.write_text(content, encoding="utf-8")
                temporary.replace(target)
            finally:
                temporary.unlink(missing_ok=True)
            return {"ok": True, "filename": filename, "message": f"Đã gửi thư đến {recipient}"}
        raise ValueError("Lệnh không hợp lệ")


class MailHandler(socketserver.BaseRequestHandler):
    def handle(self):
        self.request.settimeout(8)
        try:
            result = self.server.store.execute(receive(self.request))
            send(self.request, result)
        except (OSError, ValueError) as exc:
            error = str(exc) if isinstance(exc, ValueError) else "Lỗi kết nối hoặc truy cập dữ liệu trên máy chủ"
            try:
                send(self.request, {"ok": False, "error": error})
            except OSError:
                pass


class MailServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

    def __init__(self, address=("0.0.0.0", 2525), data_dir=None):
        self.store = MailStore(data_dir or Path(__file__).parent / "mail-data")
        super().__init__(address, MailHandler)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Mail Server bài thực hành")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=2525)
    parser.add_argument("--data-dir", type=Path, default=Path(__file__).parent / "mail-data")
    args = parser.parse_args()
    with MailServer((args.host, args.port), args.data_dir) as server:
        print(f"MailServer: {args.host}:{args.port}\nDữ liệu: {server.store.root}\nCtrl+C để dừng")
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass
