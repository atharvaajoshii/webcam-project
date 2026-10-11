"""
Python TCP client for sending Air Canvas gesture events to the Java GestureServer.

Protocol:
 UTF-8 newline-delimited ASCII strings:
 GESTURE,<NAME>,<X>,<Y>\n

 Coordinates:
 Normalized integer values in range [0, 1000].
"""

import socket
from typing import Optional

VALID_GESTURES = frozenset({"DRAW", "MOVE", "UNDO", "REDO", "CLEAR", "NONE"})


def format_gesture_message(gesture: str, x: int, y: int) -> str:
    """
    Validates and formats a gesture packet according to the protocol:
    GESTURE,<NAME>,<X>,<Y>\n

    Raises:
        ValueError: If gesture name is unknown or coordinates are outside [0, 1000].
        TypeError: If gesture is not a string or coordinates are not integers.
    """
    if not isinstance(gesture, str):
        raise TypeError(f"Gesture must be a string, got {type(gesture).__name__}")

    gesture_clean = gesture.strip()
    if gesture_clean not in VALID_GESTURES:
        raise ValueError(
            f"Invalid gesture '{gesture}'. Must be one of: {', '.join(sorted(VALID_GESTURES))}"
        )

    # Boolean is a subclass of int in Python, check for pure int
    if isinstance(x, bool) or not isinstance(x, int):
        raise TypeError(f"Coordinate x must be an integer, got {type(x).__name__}")
    if isinstance(y, bool) or not isinstance(y, int):
        raise TypeError(f"Coordinate y must be an integer, got {type(y).__name__}")

    if not (0 <= x <= 1000):
        raise ValueError(f"Coordinate x={x} out of bounds. Must be in range [0, 1000]")

    if not (0 <= y <= 1000):
        raise ValueError(f"Coordinate y={y} out of bounds. Must be in range [0, 1000]")

    return f"GESTURE,{gesture_clean},{x},{y}\n"


class GestureClient:
    """
    Reusable TCP client for streaming gesture events to the Java GestureServer.
    """

    def __init__(self, host: str = "127.0.0.1", port: int = 5000, timeout: float = 5.0):
        self.host = host
        self.port = port
        self.timeout = timeout
        self._sock: Optional[socket.socket] = None

    def connect(self) -> None:
        """Establishes connection to the Java server."""
        if self._sock is not None:
            return
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(self.timeout)
            sock.connect((self.host, self.port))
            self._sock = sock
        except (ConnectionRefusedError, socket.timeout, OSError) as e:
            try:
                sock.close()
            except OSError:
                pass
            self._sock = None
            raise ConnectionError(
                f"Failed to connect to Air Canvas Java server at {self.host}:{self.port} - {e}"
            ) from e

    def is_connected(self) -> bool:
        """Returns True if the socket is currently connected."""
        return self._sock is not None

    def send_gesture(self, gesture: str, x: int, y: int) -> None:
        """
        Validates, encodes, and sends a single gesture event.
        Automatically connects if not already connected.

        Raises:
            ValueError: If gesture name or coordinates are invalid.
            ConnectionError: If connection fails or is broken.
        """
        message = format_gesture_message(gesture, x, y)
        if self._sock is None:
            self.connect()

        try:
            self._sock.sendall(message.encode("utf-8"))
        except (BrokenPipeError, ConnectionResetError, OSError) as e:
            self.close()
            raise ConnectionError(
                f"Connection lost while sending gesture to {self.host}:{self.port} - {e}"
            ) from e

    def close(self) -> None:
        """Closes the socket connection cleanly."""
        if self._sock is not None:
            try:
                self._sock.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            finally:
                self._sock.close()
                self._sock = None

    def __enter__(self):
        self.connect()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()


def send_gesture(gesture: str, x: int, y: int, host: str = "127.0.0.1", port: int = 5000) -> None:
    """
    Convenience function to send a single gesture event over a transient connection.
    """
    with GestureClient(host=host, port=port) as client:
        client.send_gesture(gesture, x, y)
