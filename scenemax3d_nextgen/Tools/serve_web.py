"""Serve a SceneMax browser export on loopback with explicit module/Wasm MIME types.

Usage: python Tools/serve_web.py PATH_TO_EXTRACTED_EXPORT --port 8080
This is a local development server, not a public deployment server.
"""
import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


class ExportHandler(SimpleHTTPRequestHandler):
    # Windows registry MIME associations can otherwise make .mjs text/plain.
    extensions_map = {
        **SimpleHTTPRequestHandler.extensions_map,
        ".js": "text/javascript",
        ".mjs": "text/javascript",
        ".wasm": "application/wasm",
    }

    def do_GET(self):
        # A rebuilt export must not mix cached loaders with a new Wasm binary.
        for name in ("If-Modified-Since", "If-None-Match"):
            if name in self.headers:
                del self.headers[name]
        super().do_GET()

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--port", type=int, default=8080)
    args = parser.parse_args()
    directory = args.directory.resolve()
    if not (directory / "index.html").is_file():
        parser.error("directory must contain the exported index.html")
    if not 0 <= args.port <= 65535:
        parser.error("port must be between 0 and 65535")
    with ThreadingHTTPServer(
        ("127.0.0.1", args.port), partial(ExportHandler, directory=str(directory))
    ) as server:
        print(f"SceneMax web export: http://localhost:{server.server_port}/", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
