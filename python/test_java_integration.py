"""
End-to-end integration test verifying that python/tcp_client.py connects to
the actual Java GestureServer and sends validated gesture packets.
"""

import subprocess
import sys
import time
import unittest

try:
    from python.tcp_client import GestureClient
except ImportError:
    from tcp_client import GestureClient



class TestJavaGestureServerIntegration(unittest.TestCase):

    def test_python_client_with_real_java_server(self):
        # Pick a dedicated test port
        test_port = 5025

        # Launch Java GestureServer process
        java_cmd = [
            "java",
            "-cp",
            "out/production/WebcamProject",
            "aircanvas.network.GestureServer",
            str(test_port),
        ]

        proc = subprocess.Popen(
            java_cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )

        try:
            # Wait for server to announce READY
            ready_line = None
            for _ in range(50):
                line = proc.stdout.readline()
                if "READY:" in line:
                    ready_line = line.strip()
                    break
                time.sleep(0.1)

            self.assertIsNotNone(ready_line, "Java GestureServer did not start in time")
            self.assertIn(str(test_port), ready_line)

            # Connect with Python client and send gestures
            with GestureClient(host="127.0.0.1", port=test_port, timeout=3.0) as client:
                self.assertTrue(client.is_connected())
                client.send_gesture("DRAW", 123, 456)
                client.send_gesture("MOVE", 789, 999)
                client.send_gesture("UNDO", 0, 0)
                client.send_gesture("CLEAR", 0, 0)

            # Read and verify Java process output
            received = []
            for _ in range(4):
                out = proc.stdout.readline().strip()
                if out.startswith("RECEIVED:"):
                    received.append(out)

            expected = [
                "RECEIVED:DRAW:123:456",
                "RECEIVED:MOVE:789:999",
                "RECEIVED:UNDO:0:0",
                "RECEIVED:CLEAR:0:0",
            ]
            self.assertEqual(received, expected)

        finally:
            # Terminate Java process cleanly
            if proc.stdin:
                try:
                    proc.stdin.close()
                except OSError:
                    pass
            if proc.stdout:
                try:
                    proc.stdout.close()
                except OSError:
                    pass
            if proc.stderr:
                try:
                    proc.stderr.close()
                except OSError:
                    pass
            proc.terminate()
            try:
                proc.wait(timeout=2.0)
            except subprocess.TimeoutExpired:
                proc.kill()


if __name__ == "__main__":
    unittest.main()
