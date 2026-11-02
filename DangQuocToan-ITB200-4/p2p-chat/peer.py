"""P2P engine independent of the GUI. Events are delivered through a queue."""
import hashlib
import os
from pathlib import Path
import queue
import socket
import threading
import uuid

from protocol import CHUNK, MAX_FILE, read_exact, recv_json, send_json


class Peer:
    def __init__(self, name, host, server_port=9000, listen_port=0):
        self.name = name.strip()
        self.events = queue.Queue()
        self.stop = threading.Event()
        self.sockets = set()
        self.socket_lock = threading.Lock()
        self.pending = {}
        self.pending_lock = threading.Lock()
        self.slots = threading.BoundedSemaphore(8)
        self.listener = socket.socket()
        self.directory = None
        try:
            self.listener.bind(("0.0.0.0", listen_port))
            self.listener.listen(16)
            self.listener.settimeout(0.5)
            self.directory = socket.create_connection((host, server_port), timeout=8)
            send_json(self.directory, {"type": "register", "name": self.name,
                                      "port": self.listener.getsockname()[1]})
            reply = recv_json(self.directory)
            if not reply.get("ok"):
                raise ValueError(reply.get("error", "Đăng nhập thất bại"))
            self.id = reply["id"]
        except Exception:
            self.close()
            raise
        self.start(self._accept)
        self.start(self._poll)

    def start(self, target, *args):
        threading.Thread(target=target, args=args, daemon=True).start()

    def emit(self, kind, **data):
        self.events.put({"kind": kind, **data})

    def close(self):
        self.stop.set()
        with self.pending_lock:
            for answer in self.pending.values():
                answer.put(None)
        with self.socket_lock:
            sockets = list(self.sockets)
        for sock in [self.directory, self.listener, *sockets]:
            if sock:
                try:
                    sock.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                sock.close()

    def _poll(self):
        try:
            while not self.stop.is_set():
                send_json(self.directory, {"type": "list"})
                reply = recv_json(self.directory)
                if not reply.get("ok"):
                    raise ValueError(reply.get("error", "Lỗi danh bạ"))
                self.emit("peers", peers=reply["peers"])
                self.stop.wait(2)
        except (OSError, ValueError, ConnectionError) as exc:
            if not self.stop.is_set():
                self.emit("offline", message=f"Mất kết nối máy chủ: {exc}. Hãy đăng nhập lại.")
                self.close()

    def _accept(self):
        while not self.stop.is_set():
            try:
                sock, _ = self.listener.accept()
            except socket.timeout:
                continue
            except OSError:
                break
            if not self.slots.acquire(blocking=False):
                sock.close()
                continue
            self.start(self._receive, sock)

    def decide(self, transfer_id, path):
        with self.pending_lock:
            answer = self.pending.get(transfer_id)
            if answer:
                answer.put(path)

    def _receive(self, sock):
        partial = None
        transfer_id = uuid.uuid4().hex
        with self.socket_lock:
            self.sockets.add(sock)
        try:
            with sock:
                sock.settimeout(30)
                request = recv_json(sock)
                sender = str(request.get("name", "Ẩn danh"))[:30]
                sender_id = str(request.get("id", ""))[:64]
                if request.get("type") == "chat":
                    message = request.get("text")
                    if not isinstance(message, str) or not 0 < len(message) <= 4000:
                        raise ValueError("Tin nhắn không hợp lệ")
                    self.emit("chat", peer_id=sender_id, name=sender, text=message)
                    send_json(sock, {"ok": True})
                    return
                if request.get("type") != "file":
                    raise ValueError("Loại dữ liệu không hợp lệ")
                size = request.get("size")
                if type(size) is not int or not 0 <= size <= MAX_FILE:
                    raise ValueError("File phải nhỏ hơn hoặc bằng 2 GiB")
                filename = str(request.get("filename", "file"))[:255]
                filename = filename.replace("\\", "/").split("/")[-1] or "file"
                answer = queue.Queue()
                with self.pending_lock:
                    self.pending[transfer_id] = answer
                self.emit("offer", transfer_id=transfer_id, name=sender, filename=filename, size=size)
                try:
                    destination = answer.get(timeout=90)
                except queue.Empty:
                    destination = None
                finally:
                    with self.pending_lock:
                        self.pending.pop(transfer_id, None)
                if not destination or self.stop.is_set():
                    send_json(sock, {"ok": False, "error": "Người nhận từ chối hoặc hết thời gian chờ"})
                    return
                destination = Path(destination)
                partial = destination.with_name(destination.name + "." + transfer_id + ".part")
                digest = hashlib.sha256()
                received = 0
                with partial.open("xb") as output:
                    send_json(sock, {"ok": True})
                    while received < size:
                        block = read_exact(sock, min(CHUNK, size - received))
                        output.write(block)
                        digest.update(block)
                        received += len(block)
                        self.progress(transfer_id, filename, received, size)
                trailer = recv_json(sock)
                if trailer.get("sha256") != digest.hexdigest():
                    raise ValueError("SHA-256 không khớp, file bị lỗi")
                os.replace(partial, destination)
                partial = None
                send_json(sock, {"ok": True})
                self.emit("done", transfer_id=transfer_id, message=f"Đã nhận {filename} → {destination} (SHA-256 khớp)")
        except (OSError, ValueError, ConnectionError) as exc:
            if not self.stop.is_set():
                self.emit("error", transfer_id=transfer_id, message=f"Nhận dữ liệu thất bại: {exc}")
        finally:
            if partial:
                partial.unlink(missing_ok=True)
            with self.socket_lock:
                self.sockets.discard(sock)
            self.slots.release()

    def progress(self, transfer_id, filename, count, total):
        # Emit at most about 100 events per file, including final byte.
        step = max(CHUNK, (total // 100 // CHUNK) * CHUNK)
        if count == total or count % step == 0:
            self.emit("progress", transfer_id=transfer_id, filename=filename,
                      percent=100 if total == 0 else count * 100 / total)

    def send_chat(self, peer, message):
        if not message.strip() or len(message) > 4000:
            raise ValueError("Tin nhắn phải có từ 1 đến 4000 ký tự")
        self.start(self._send, dict(peer), message, None)

    def send_file(self, peer, path):
        self.start(self._send, dict(peer), None, Path(path))

    def _send(self, peer, message, path):
        transfer_id = uuid.uuid4().hex
        sock = None
        try:
            if self.stop.is_set():
                raise ConnectionError("Bạn đã ngắt kết nối")
            sock = socket.create_connection((peer["host"], peer["port"]), timeout=8)
            with self.socket_lock:
                self.sockets.add(sock)
            with sock:
                sock.settimeout(100)
                header = {"id": self.id, "name": self.name}
                if path is None:
                    send_json(sock, dict(header, type="chat", text=message))
                    reply = recv_json(sock)
                    if not reply.get("ok"):
                        raise ValueError(reply.get("error", "Không gửi được tin"))
                    self.emit("sent", peer_id=peer["id"], name=peer["name"], text=message)
                    return
                with path.open("rb") as source:
                    size = os.fstat(source.fileno()).st_size
                    if size > MAX_FILE:
                        raise ValueError("File vượt quá 2 GiB")
                    send_json(sock, dict(header, type="file", filename=path.name, size=size))
                    self.emit("status", message=f"Đợi {peer['name']} chấp nhận {path.name}…")
                    reply = recv_json(sock)
                    if not reply.get("ok"):
                        raise ValueError(reply.get("error", "File bị từ chối"))
                    sock.settimeout(30)
                    digest = hashlib.sha256()
                    count = 0
                    while count < size:
                        block = source.read(min(CHUNK, size - count))
                        if not block:
                            raise ValueError("File nguồn đã thay đổi trong khi gửi")
                        sock.sendall(block)
                        digest.update(block)
                        count += len(block)
                        self.progress(transfer_id, path.name, count, size)
                    send_json(sock, {"sha256": digest.hexdigest()})
                    reply = recv_json(sock)
                    if not reply.get("ok"):
                        raise ValueError("Người nhận chưa xác nhận file")
                self.emit("done", transfer_id=transfer_id, message=f"Đã gửi {path.name} cho {peer['name']} (SHA-256 khớp)")
        except (OSError, ValueError, ConnectionError, KeyError) as exc:
            self.emit("error", transfer_id=transfer_id, message=f"Gửi thất bại: {exc}")
        finally:
            if sock:
                with self.socket_lock:
                    self.sockets.discard(sock)
