from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path
import socket
import struct
import tempfile
import threading
import unittest

from ExchangeRateServer import ExchangeRateServer
from MailServer import MailServer, MailStore, WELCOME
from network import request, receive, MAX_MESSAGE


class LabTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.rates = ExchangeRateServer(("127.0.0.1", 0))
        self.mail = MailServer(("127.0.0.1", 0), self.temp.name)
        for server in (self.rates, self.mail):
            threading.Thread(target=server.serve_forever, daemon=True).start()

    def tearDown(self):
        for server in (self.rates, self.mail):
            server.shutdown()
            server.server_close()
        self.temp.cleanup()

    def rpc(self, action, **data):
        return request("127.0.0.1", self.mail.server_address[1], dict(data, action=action))

    def account(self, name):
        self.rpc("register", username=name, password="pass1234")
        return self.rpc("login", username=name, password="pass1234")["token"]

    def test_rates_markets_time_and_range(self):
        result = request("127.0.0.1", self.rates.server_address[1], {"action": "rates"})
        self.assertIsNotNone(datetime.fromisoformat(result["timestamp"]).tzinfo)
        self.assertEqual([r["market"] for r in result["rates"]], ["Tokyo", "Newyork", "Hồng Kông"])
        for rate, (low, high) in zip(result["rates"], [(140, 155), (1.02, 1.15), (7.75, 7.85)]):
            self.assertTrue(low <= rate["value"] <= high)

    def test_registration_welcome_and_login_listing(self):
        token = self.account("Alice")
        result = self.rpc("list", token=token)
        self.assertEqual(result["files"], ["new_email.txt"])
        mailbox = Path(self.temp.name) / "mailboxes" / "alice"
        self.assertEqual((mailbox / "new_email.txt").read_text(encoding="utf-8"), WELCOME)
        self.assertEqual(self.rpc("read", token=token, filename="new_email.txt")["content"], WELCOME)
        self.assertNotIn("pass1234", (Path(self.temp.name) / "accounts.json").read_text())

    def test_send_offline_recipient_and_persistence(self):
        alice = self.account("alice")
        self.rpc("register", username="bob", password="pass1234")
        sent = self.rpc("send", token=alice, to="bob", subject="Chào bạn", body="Nội dung tiếng Việt\nDòng 2")
        login = self.rpc("login", username="bob", password="pass1234")
        self.assertIn(sent["filename"], login["files"])
        content = self.rpc("read", token=login["token"], filename=sent["filename"])["content"]
        self.assertIn("Nội dung tiếng Việt\nDòng 2", content)
        self.assertIn("From: alice", content)
        restored = MailStore(self.temp.name)
        self.assertIn(sent["filename"], restored.execute({"action": "login", "username": "bob", "password": "pass1234"})["files"])

    def test_invalid_credentials_paths_and_recipient(self):
        alice = self.account("alice")
        for payload in [
            {"action": "register", "username": "../outside", "password": "pass1234"},
            {"action": "register", "username": "ALICE", "password": "pass1234"},
            {"action": "login", "username": "alice", "password": "wrong"},
            {"action": "read", "token": alice, "filename": "../../accounts.json"},
            {"action": "send", "token": alice, "to": "nobody", "subject": "hi", "body": "hello"},
            {"action": "list", "token": "invalid"},
            {"action": "send", "token": alice, "to": "alice", "subject": "hi", "body": ""},
        ]:
            with self.subTest(payload=payload), self.assertRaises(ValueError):
                self.rpc(**payload)
        self.rpc("logout", token=alice)
        with self.assertRaises(ValueError):
            self.rpc("list", token=alice)

    def test_concurrent_delivery_unique_files(self):
        token = self.account("alice")
        with ThreadPoolExecutor(max_workers=5) as pool:
            replies = list(pool.map(lambda i: self.rpc("send", token=token, to="alice", subject=str(i), body=f"Mail {i}"), range(10)))
        self.assertEqual(len({reply["filename"] for reply in replies}), 10)
        self.assertEqual(len(self.rpc("list", token=token)["files"]), 11)

    def test_oversize_frame_rejected(self):
        with socket.create_connection(self.mail.server_address, timeout=3) as sock:
            sock.sendall(struct.pack("!I", MAX_MESSAGE + 1))
            self.assertFalse(receive(sock)["ok"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
