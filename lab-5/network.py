"""Giao thức TCP: 4 byte độ dài big-endian + JSON UTF-8."""
import json
import socket
import struct

MAX_MESSAGE = 2 * 1024 * 1024


def read_exact(sock, size):
    data = bytearray()
    while len(data) < size:
        block = sock.recv(size - len(data))
        if not block:
            raise ConnectionError("Kết nối đã đóng")
        data.extend(block)
    return bytes(data)


def receive(sock):
    size = struct.unpack("!I", read_exact(sock, 4))[0]
    if not 0 < size <= MAX_MESSAGE:
        raise ValueError("Thông điệp vượt giới hạn")
    value = json.loads(read_exact(sock, size).decode("utf-8"))
    if not isinstance(value, dict):
        raise ValueError("Thông điệp không hợp lệ")
    return value


def send(sock, value):
    data = json.dumps(value, ensure_ascii=False).encode("utf-8")
    if len(data) > MAX_MESSAGE:
        raise ValueError("Thông điệp vượt giới hạn")
    sock.sendall(struct.pack("!I", len(data)) + data)


def request(host, port, payload):
    with socket.create_connection((host, port), timeout=5) as sock:
        sock.settimeout(8)
        send(sock, payload)
        reply = receive(sock)
    if not reply.get("ok"):
        raise ValueError(reply.get("error", "Yêu cầu thất bại"))
    return reply
