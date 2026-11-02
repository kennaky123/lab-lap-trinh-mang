"""Length-prefixed JSON control frames, followed by raw bytes for files."""
import json
import struct

MAX_FRAME = 65536
CHUNK = 65536
MAX_FILE = 2 * 1024**3


def read_exact(sock, size):
    data = bytearray()
    while len(data) < size:
        block = sock.recv(size - len(data))
        if not block:
            raise ConnectionError("Kết nối đã đóng trước khi nhận đủ dữ liệu")
        data.extend(block)
    return bytes(data)


def send_json(sock, value):
    data = json.dumps(value, ensure_ascii=False).encode("utf-8")
    if len(data) > MAX_FRAME:
        raise ValueError("Thông điệp quá dài")
    sock.sendall(struct.pack("!I", len(data)) + data)


def recv_json(sock):
    size = struct.unpack("!I", read_exact(sock, 4))[0]
    if not 0 < size <= MAX_FRAME:
        raise ValueError("Độ dài thông điệp không hợp lệ")
    value = json.loads(read_exact(sock, size))
    if not isinstance(value, dict):
        raise ValueError("Thông điệp phải là JSON object")
    return value
