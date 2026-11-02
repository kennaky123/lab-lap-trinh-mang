"""Bài 1: máy chủ tỷ giá mô phỏng, TCP 2345."""
import argparse
from datetime import datetime
import random
import socketserver

from network import receive, send


class RateHandler(socketserver.BaseRequestHandler):
    def handle(self):
        self.request.settimeout(8)
        try:
            if receive(self.request).get("action") != "rates":
                raise ValueError("Chỉ hỗ trợ lệnh rates")
            rates = [
                {"market": "Tokyo", "pair": "USD/JPY", "value": round(random.uniform(140, 155), 4)},
                {"market": "Newyork", "pair": "EUR/USD", "value": round(random.uniform(1.02, 1.15), 4)},
                {"market": "Hồng Kông", "pair": "USD/HKD", "value": round(random.uniform(7.75, 7.85), 4)},
            ]
            send(self.request, {"ok": True, "timestamp": datetime.now().astimezone().isoformat(timespec="seconds"), "rates": rates})
        except (OSError, ValueError) as exc:
            try:
                send(self.request, {"ok": False, "error": str(exc)})
            except OSError:
                pass


class ExchangeRateServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

    def __init__(self, address=("0.0.0.0", 2345)):
        super().__init__(address, RateHandler)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="ExchangeRateServer — tỷ giá mô phỏng")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=2345)
    args = parser.parse_args()
    with ExchangeRateServer((args.host, args.port)) as server:
        print(f"ExchangeRateServer: {args.host}:{args.port} — Ctrl+C để dừng")
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass
