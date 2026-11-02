"""Directory server: never receives chat text or file contents."""
import argparse
import socketserver
import threading
import uuid

from protocol import recv_json, send_json


class DirectoryServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

    def __init__(self, address):
        super().__init__(address, Handler)
        self.peers = {}
        self.lock = threading.Lock()


class Handler(socketserver.BaseRequestHandler):
    def handle(self):
        peer_id = None
        self.request.settimeout(20)
        try:
            first = recv_json(self.request)
            name = str(first.get("name", "")).strip()
            port = first.get("port")
            if first.get("type") != "register" or not 1 <= len(name) <= 30:
                raise ValueError("Tên phải dài từ 1 đến 30 ký tự")
            if type(port) is not int or not 1 <= port <= 65535:
                raise ValueError("Cổng P2P không hợp lệ")
            with self.server.lock:
                if any(p["name"].casefold() == name.casefold() for p in self.server.peers.values()):
                    raise ValueError("Tên đang được sử dụng")
                peer_id = uuid.uuid4().hex
                self.server.peers[peer_id] = {
                    "id": peer_id, "name": name,
                    "host": self.client_address[0], "port": port,
                }
            send_json(self.request, {"ok": True, "id": peer_id})
            while True:
                request = recv_json(self.request)
                if request.get("type") == "bye":
                    break
                if request.get("type") != "list":
                    raise ValueError("Lệnh không hợp lệ")
                with self.server.lock:
                    peers = [dict(p) for key, p in self.server.peers.items() if key != peer_id]
                send_json(self.request, {"ok": True, "peers": peers})
        except (OSError, ValueError, ConnectionError) as exc:
            try:
                send_json(self.request, {"ok": False, "error": str(exc)})
            except OSError:
                pass
        finally:
            with self.server.lock:
                self.server.peers.pop(peer_id, None)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Máy chủ danh bạ P2P")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=9000)
    args = parser.parse_args()
    with DirectoryServer((args.host, args.port)) as server:
        print(f"Máy chủ danh bạ chạy tại {args.host}:{args.port}. Ctrl+C để dừng.")
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass
