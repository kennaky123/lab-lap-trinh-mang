import hashlib
from pathlib import Path
import queue
import socket
import tempfile
import threading
import time
import unittest

from peer import Peer
from protocol import recv_json, send_json
from server import DirectoryServer


def wait_event(peer, kind, timeout=8):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            event = peer.events.get(timeout=max(0.01, deadline - time.monotonic()))
        except queue.Empty:
            break
        if event["kind"] == kind:
            return event
    raise AssertionError(f"Không nhận được sự kiện {kind}")


class IntegrationTests(unittest.TestCase):
    def setUp(self):
        self.server = DirectoryServer(("127.0.0.1", 0))
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.port = self.server.server_address[1]
        self.alice = Peer("Alice", "127.0.0.1", self.port)
        self.bob = Peer("Bob", "127.0.0.1", self.port)
        self.target = {"id": self.bob.id, "name": "Bob", "host": "127.0.0.1",
                       "port": self.bob.listener.getsockname()[1]}
        self.temp = tempfile.TemporaryDirectory()

    def tearDown(self):
        self.alice.close()
        self.bob.close()
        self.server.shutdown()
        self.server.server_close()
        self.temp.cleanup()

    def test_discovery_chat_and_disconnect(self):
        deadline = time.monotonic() + 6
        while time.monotonic() < deadline:
            online = wait_event(self.alice, "peers")["peers"]
            if any(p["id"] == self.bob.id for p in online):
                break
        else:
            self.fail("Bob không xuất hiện trong danh bạ")
        self.alice.send_chat(self.target, "Xin chào! Tiếng Việt ✓")
        self.assertEqual(wait_event(self.bob, "chat")["text"], "Xin chào! Tiếng Việt ✓")
        self.assertEqual(wait_event(self.alice, "sent")["peer_id"], self.bob.id)
        self.bob.close()
        deadline = time.monotonic() + 6
        while time.monotonic() < deadline:
            if not wait_event(self.alice, "peers")["peers"]:
                break
        else:
            self.fail("Bob vẫn còn trong danh bạ")

    def test_binary_and_empty_file(self):
        for contents in (bytes(range(256)) * 10000, b""):
            with self.subTest(size=len(contents)):
                source = Path(self.temp.name) / "nguồn.bin"
                destination = Path(self.temp.name) / "đích.bin"
                source.write_bytes(contents)
                self.alice.send_file(self.target, source)
                offer = wait_event(self.bob, "offer")
                self.bob.decide(offer["transfer_id"], destination)
                wait_event(self.bob, "done")
                wait_event(self.alice, "done")
                self.assertEqual(hashlib.sha256(destination.read_bytes()).digest(), hashlib.sha256(contents).digest())
                self.assertFalse(list(Path(self.temp.name).glob("*.part")))

    def test_rejected_file(self):
        source = Path(self.temp.name) / "test.txt"
        source.write_text("hello")
        self.alice.send_file(self.target, source)
        offer = wait_event(self.bob, "offer")
        self.bob.decide(offer["transfer_id"], None)
        self.assertIn("từ chối", wait_event(self.alice, "error")["message"])

    def test_duplicate_name(self):
        with self.assertRaisesRegex(ValueError, "đang được sử dụng"):
            Peer("ALICE", "127.0.0.1", self.port)

    def test_bad_hash_preserves_existing_destination(self):
        destination = Path(self.temp.name) / "existing.txt"
        destination.write_bytes(b"original")
        with socket.create_connection(("127.0.0.1", self.target["port"])) as sock:
            sock.settimeout(5)
            send_json(sock, {"type": "file", "name": "Test", "filename": "../existing.txt", "size": 3})
            offer = wait_event(self.bob, "offer")
            self.assertEqual(offer["filename"], "existing.txt")
            self.bob.decide(offer["transfer_id"], destination)
            self.assertTrue(recv_json(sock)["ok"])
            sock.sendall(b"bad")
            send_json(sock, {"sha256": "incorrect"})
            self.assertIn("SHA-256", wait_event(self.bob, "error")["message"])
        # The event precedes cleanup; wait for the receiver's finally block.
        deadline = time.monotonic() + 3
        while list(Path(self.temp.name).glob("*.part")) and time.monotonic() < deadline:
            time.sleep(0.01)
        self.assertEqual(destination.read_bytes(), b"original")
        self.assertFalse(list(Path(self.temp.name).glob("*.part")))


if __name__ == "__main__":
    unittest.main(verbosity=2)
