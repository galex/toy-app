"""Shared HTTP client for the probe dev server.

The probe is a plain HTTP+JSON server embedded in the debug build. This module knows how to call
each endpoint, and is the single source of truth for the port. Both the `probe` CLI and the
`run-flow` YAML runner import it.

Standard library only. No pip install, no build step, no virtualenv.
"""
from __future__ import annotations

import json
import urllib.error
import urllib.request

# The one place the port is written down. Everything else, including CI shell scripts, resolves it
# through here rather than repeating the number.
DEFAULT_PORT = 4242


class ProbeError(RuntimeError):
    """A probe call failed: connection refused, a non-2xx status, or a tool-level error."""


class ProbeClient:
    def __init__(self, host: str = "127.0.0.1", port: int = DEFAULT_PORT, timeout: float = 15.0):
        self.base = f"http://{host}:{port}"
        self.timeout = timeout

    # --- transport ---------------------------------------------------------

    def _request(self, method: str, path: str, body: dict | None = None) -> tuple[int, bytes, str]:
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(self.base + path, data=data, method=method)
        if data is not None:
            req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                return resp.status, resp.read(), resp.headers.get("Content-Type", "")
        except urllib.error.HTTPError as e:
            # Not an error path: a 400 still carries our JSON body, and the caller needs to read it.
            return e.code, e.read(), e.headers.get("Content-Type", "")
        except urllib.error.URLError as e:
            # Written for the agent reading it: say what to do next, not just what went wrong.
            raise ProbeError(
                f"cannot reach probe at {self.base} ({e.reason}). Is a debug build running, and "
                f"did you forward the port with probe/scripts/forward.sh?"
            )

    def _json(self, method: str, path: str, body: dict | None = None) -> dict:
        status, raw, _ctype = self._request(method, path, body)
        try:
            payload = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            raise ProbeError(f"{path} returned non-JSON (status {status}): {raw[:200]!r}")
        if status >= 400 or (isinstance(payload, dict) and payload.get("ok") is False):
            msg = payload.get("error", raw) if isinstance(payload, dict) else raw
            raise ProbeError(f"{path} failed (status {status}): {msg}")
        return payload

    # --- tools -------------------------------------------------------------

    def app_info(self) -> dict:
        return self._json("GET", "/app_info")

    def ui_snapshot(self) -> dict:
        return self._json("GET", "/ui_snapshot")

    def logs(self) -> dict:
        return self._json("GET", "/logs")

    def screenshot(self) -> bytes:
        status, raw, _ctype = self._request("GET", "/screenshot")
        if status >= 400:
            raise ProbeError(f"/screenshot failed (status {status}): {raw[:200]!r}")
        return raw

    def tap(self, x: float, y: float) -> dict:
        return self._json("POST", "/tap", {"x": x, "y": y})

    def swipe(self, start_x, start_y, end_x, end_y, duration_ms: int = 300) -> dict:
        return self._json(
            "POST",
            "/swipe",
            {
                "startX": start_x,
                "startY": start_y,
                "endX": end_x,
                "endY": end_y,
                "durationMs": duration_ms,
            },
        )

    def input_text(self, text: str) -> dict:
        return self._json("POST", "/input_text", {"text": text})

    def press_back(self) -> dict:
        return self._json("POST", "/press_back")
