"""
Unit and integration tests for python/tcp_client.py
"""

import socket
import threading
import unittest

from python.tcp_client import GestureClient, format_gesture_message, send_gesture


class TestProtocolFormatting(unittest.TestCase):

    def test_valid_formatting_all_gestures(self):
        gestures = ["DRAW", "MOVE", "UNDO", "REDO", "CLEAR", "NONE"]
        for g in gestures:
            msg = format_gesture_message(g, 500, 400)
            self.assertEqual(msg, f"GESTURE,{g},500,400\n")
            self.assertTrue(msg.endswith("\n"))
            # Confirm valid UTF-8 encode/decode
            encoded = msg.encode("utf-8")
            self.assertEqual(encoded.decode("utf-8"), msg)

    def test_boundary_coordinates(self):
        msg_min = format_gesture_message("DRAW", 0, 0)
        self.assertEqual(msg_min, "GESTURE,DRAW,0,0\n")

        msg_max = format_gesture_message("MOVE", 1000, 1000)
        self.assertEqual(msg_max, "GESTURE,MOVE,1000,1000\n")

    def test_invalid_gesture_names(self):
        invalid = ["ZOOM", "draw", "Move", "", "INVALID", "PINCH"]
        for g in invalid:
            with self.assertRaises(ValueError):
                format_gesture_message(g, 500, 500)

    def test_out_of_bounds_coordinates(self):
        # Negative coords
        with self.assertRaises(ValueError):
            format_gesture_message("DRAW", -1, 500)
        with self.assertRaises(ValueError):
            format_gesture_message("DRAW", 500, -1)

        # > 1000 coords
        with self.assertRaises(ValueError):
            format_gesture_message("DRAW", 1001, 500)
        with self.assertRaises(ValueError):
            format_gesture_message("DRAW", 500, 1001)

    def test_type_errors(self):
        with self.assertRaises(TypeError):
            format_gesture_message(123, 500, 500)
        with self.assertRaises(TypeError):
            format_gesture_message("DRAW", "500", 500)
        with self.assertRaises(TypeError):
            format_gesture_message("DRAW", 500, "500")
        with self.assertRaises(TypeError):
            format_gesture_message("DRAW", True, 500)  # bool rejected


class TestGestureClientNetworking(unittest.TestCase):

    def test_connection_refusal(self):
        # Port 1 is reserved and typically not open
        client = GestureClient(host="127.0.0.1", port=1, timeout=0.5)
        with self.assertRaises(ConnectionError):
            client.connect()
        self.assertFalse(client.is_connected())

    def test_clean_shutdown(self):
        client = GestureClient(host="127.0.0.1", port=5000)
        client.close()  # Closing an unconnected client should be a safe no-op
        self.assertFalse(client.is_connected())

    def test_end_to_end_with_mock_server(self):
        # Create a local mock server on an ephemeral port
        server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_sock.bind(("127.0.0.1", 0))
        server_port = server_sock.getsockname()[1]
        server_sock.listen(1)

        received_lines = []

        def mock_server_worker():
            conn, _ = server_sock.accept()
            with conn:
                f = conn.makefile("r", encoding="utf-8")
                for line in f:
                    received_lines.append(line)

        server_thread = threading.Thread(target=mock_server_worker, daemon=True)
        server_thread.start()

        # Connect client and send multiple events
        with GestureClient(host="127.0.0.1", port=server_port, timeout=2.0) as client:
            self.assertTrue(client.is_connected())
            client.send_gesture("DRAW", 200, 300)
            client.send_gesture("MOVE", 400, 500)
            client.send_gesture("UNDO", 0, 0)
            client.send_gesture("CLEAR", 0, 0)

        server_thread.join(timeout=2.0)
        server_sock.close()

        # Verify all lines received correctly
        expected = [
            "GESTURE,DRAW,200,300\n",
            "GESTURE,MOVE,400,500\n",
            "GESTURE,UNDO,0,0\n",
            "GESTURE,CLEAR,0,0\n",
        ]
        self.assertEqual(received_lines, expected)

    def test_send_gesture_convenience_function(self):
        server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_sock.bind(("127.0.0.1", 0))
        server_port = server_sock.getsockname()[1]
        server_sock.listen(1)

        received_lines = []

        def mock_server_worker():
            conn, _ = server_sock.accept()
            with conn:
                f = conn.makefile("r", encoding="utf-8")
                for line in f:
                    received_lines.append(line)

        server_thread = threading.Thread(target=mock_server_worker, daemon=True)
        server_thread.start()

        # Use send_gesture standalone function
        send_gesture("REDO", 10, 20, host="127.0.0.1", port=server_port)

        server_thread.join(timeout=2.0)
        server_sock.close()

        self.assertEqual(received_lines, ["GESTURE,REDO,10,20\n"])


if __name__ == "__main__":
    unittest.main()
