#!/usr/bin/env python3
"""
SceneMax UDP multiplayer server load test.

This tool speaks the raw SceneMax multiplayer protocol directly. It creates many
lightweight UDP clients, logs them into one session/scene, creates one entity per
client, sends transform corrections at a configurable rate, and sends low-rate
latency marker commands that every other client should receive.
"""

import argparse
import hashlib
import json
import math
import random
import select
import socket
import statistics
import struct
import time
from dataclasses import dataclass, field
from pathlib import Path


MAGIC = 0x504D5853
VERSION = 1
LOGIN_REQUEST = 1
LOGIN_ACCEPTED = 2
LOGIN_REJECTED = 3
HEARTBEAT = 4
CREATE_ENTITY_REQUEST = 10
CREATE_ENTITY_ACCEPTED = 11
COMMAND_DISPATCH = 20
TRANSFORM_CORRECTION = 21
NETWORK_EVENT = 24
SNAPSHOT = 30
DISCONNECT = 40

MAX_PACKET_SIZE = 1200
DEFAULT_PROJECT_GUID = "845fa4dd-4351-4924-89a9-b7700a90fec6"


def fixed(value: str, size: int) -> bytes:
    raw = (value or "").encode("utf-8")[: max(0, size - 1)]
    return raw + bytes(size - len(raw))


def read_fixed(raw: bytes) -> str:
    end = raw.find(b"\0")
    if end >= 0:
        raw = raw[:end]
    return raw.decode("utf-8", errors="replace")


def packet(packet_type: int, client_id: int, payload: bytes = b"") -> bytes:
    return struct.pack("<IBBH", MAGIC, VERSION, packet_type, client_id) + payload


def percentile(values, percent):
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, math.ceil((percent / 100.0) * len(ordered)) - 1))
    return ordered[index]


@dataclass
class SimClient:
    index: int
    sock: socket.socket
    client_id: int = 0
    session_id: int = 0
    network_id: int = 0
    create_request_id: int = 1
    last_login_send: float = 0.0
    last_create_send: float = 0.0
    next_heartbeat: float = 0.0
    next_transform: float = 0.0
    next_command: float = 0.0
    command_seq: int = 0
    rejected: bool = False
    recv_by_type: dict = field(default_factory=dict)

    @property
    def logged_in(self) -> bool:
        return self.client_id != 0 and not self.rejected

    @property
    def ready(self) -> bool:
        return self.logged_in and self.network_id != 0


class LoadTest:
    def __init__(self, args):
        self.args = args
        self.server = (args.server, args.port)
        self.clients = []
        self.command_sends = 0
        self.transform_sends = 0
        self.heartbeat_sends = 0
        self.command_relays = 0
        self.duplicate_relays = 0
        self.latencies_ms = []
        self.received_markers = set()
        self.recv_by_type = {}

        for i in range(args.clients):
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.bind((args.bind, 0))
            sock.connect(self.server)
            sock.setblocking(False)
            self.clients.append(SimClient(i, sock, create_request_id=i + 1))

    def close(self):
        for client in self.clients:
            try:
                if client.client_id:
                    client.sock.send(packet(DISCONNECT, client.client_id))
            except OSError:
                pass
            client.sock.close()

    def send_login(self, client: SimClient, now: float):
        payload = hashlib.sha256((self.args.password or "").encode("utf-8")).digest()
        payload += fixed(self.args.project_guid, 64)
        payload += struct.pack("<BI", 1 if self.args.create_session else 0, self.args.session_id)
        payload += fixed(self.args.session_name, 64)
        payload += fixed(self.args.scene, 128)
        payload += fixed(f"load_{client.index}", 64)
        client.sock.send(packet(LOGIN_REQUEST, 0, payload))
        client.last_login_send = now

    def send_create(self, client: SimClient, now: float):
        payload = struct.pack("<I", client.create_request_id)
        payload += fixed(self.args.archetype, 64)
        payload += fixed(f"load_{client.index}", 64)
        payload += fixed(f"load_entity_{client.index}", 64)
        payload += fixed("", 256)
        client.sock.send(packet(CREATE_ENTITY_REQUEST, client.client_id, payload))
        client.last_create_send = now

    def send_heartbeat(self, client: SimClient, now: float):
        client.sock.send(packet(HEARTBEAT, client.client_id))
        client.next_heartbeat = now + 1.0
        self.heartbeat_sends += 1

    def send_transform(self, client: SimClient, now: float):
        t = now - self.run_started_at
        angle = t * 1.7 + client.index * 0.37
        x = math.sin(angle) * self.args.motion_radius
        y = (client.index % 8) * 0.25
        z = math.cos(angle) * self.args.motion_radius
        payload = struct.pack("<Ifffffff", client.network_id, x, y, z, 0.0, 0.0, 0.0, 1.0)
        client.sock.send(packet(TRANSFORM_CORRECTION, client.client_id, payload))
        client.next_transform += self.transform_interval
        self.transform_sends += 1

    def send_command(self, client: SimClient, now: float):
        client.command_seq += 1
        sent_ns = time.perf_counter_ns()
        text = f"LT|{client.index}|{client.command_seq}|{sent_ns}"
        payload = struct.pack("<I", client.network_id) + text.encode("utf-8")
        client.sock.send(packet(COMMAND_DISPATCH, client.client_id, payload))
        client.next_command += self.command_interval
        self.command_sends += 1

    def receive_available(self, end_time: float):
        socket_to_client = {client.sock: client for client in self.clients}
        while time.perf_counter() < end_time:
            readable, _, _ = select.select(list(socket_to_client.keys()), [], [], 0.01)
            if not readable:
                return
            for sock in readable:
                client = socket_to_client[sock]
                while True:
                    try:
                        data = sock.recv(MAX_PACKET_SIZE)
                    except BlockingIOError:
                        break
                    except ConnectionResetError:
                        break
                    self.handle_packet(client, data)

    def handle_packet(self, client: SimClient, data: bytes):
        if len(data) < 8:
            return
        magic, version, packet_type, sender_id = struct.unpack_from("<IBBH", data, 0)
        if magic != MAGIC or version != VERSION:
            return
        payload = data[8:]
        client.recv_by_type[packet_type] = client.recv_by_type.get(packet_type, 0) + 1
        self.recv_by_type[packet_type] = self.recv_by_type.get(packet_type, 0) + 1

        if packet_type == LOGIN_ACCEPTED and len(payload) >= 70:
            accepted_client_id, session_id = struct.unpack_from("<HI", payload, 0)
            client.client_id = accepted_client_id
            client.session_id = session_id
        elif packet_type == LOGIN_REJECTED:
            client.rejected = True
        elif packet_type == CREATE_ENTITY_ACCEPTED and len(payload) >= 4:
            network_id = struct.unpack_from("<I", payload, 0)[0]
            create_request_id = struct.unpack_from("<I", payload, 4)[0] if len(payload) >= 8 else 0
            if sender_id == client.client_id and (create_request_id == 0 or create_request_id == client.create_request_id):
                client.network_id = network_id
        elif packet_type == COMMAND_DISPATCH and len(payload) > 4:
            text = payload[4:].decode("utf-8", errors="replace").strip("\0\r\n ")
            self.handle_command_relay(client, text)
        elif packet_type == SNAPSHOT:
            pass

    def handle_command_relay(self, receiver: SimClient, text: str):
        if not text.startswith("LT|"):
            return
        parts = text.split("|")
        if len(parts) != 4:
            return
        try:
            sender_index = int(parts[1])
            seq = int(parts[2])
            sent_ns = int(parts[3])
        except ValueError:
            return
        if sender_index == receiver.index:
            return
        key = (receiver.index, sender_index, seq)
        if key in self.received_markers:
            self.duplicate_relays += 1
            return
        self.received_markers.add(key)
        self.command_relays += 1
        self.latencies_ms.append((time.perf_counter_ns() - sent_ns) / 1_000_000.0)

    def wait_for_logins(self):
        deadline = time.perf_counter() + self.args.stage_timeout
        while time.perf_counter() < deadline:
            now = time.perf_counter()
            for client in self.clients:
                if not client.logged_in and not client.rejected and now - client.last_login_send >= 0.25:
                    self.send_login(client, now)
            self.receive_available(now + 0.03)
            if all(client.logged_in or client.rejected for client in self.clients):
                break

    def wait_for_creates(self):
        deadline = time.perf_counter() + self.args.stage_timeout
        while time.perf_counter() < deadline:
            now = time.perf_counter()
            for client in self.clients:
                if client.logged_in and not client.ready and now - client.last_create_send >= 0.25:
                    self.send_create(client, now)
            self.receive_available(now + 0.03)
            if all(client.ready or client.rejected for client in self.clients):
                break

    def run_load(self):
        ready = [client for client in self.clients if client.ready]
        if not ready:
            raise RuntimeError("No simulated clients reached ready state.")

        self.transform_interval = 1.0 / self.args.transform_rate if self.args.transform_rate > 0 else None
        self.command_interval = 1.0 / self.args.command_rate if self.args.command_rate > 0 else None
        self.run_started_at = time.perf_counter()

        for client in ready:
            jitter = random.random() * 0.25
            client.next_heartbeat = self.run_started_at + jitter
            client.next_transform = self.run_started_at + jitter
            client.next_command = self.run_started_at + jitter

        end = self.run_started_at + self.args.duration
        while time.perf_counter() < end:
            now = time.perf_counter()
            for client in ready:
                if now >= client.next_heartbeat:
                    self.send_heartbeat(client, now)
                if self.transform_interval is not None:
                    while now >= client.next_transform:
                        self.send_transform(client, now)
                if self.command_interval is not None:
                    while now >= client.next_command:
                        self.send_command(client, now)
            self.receive_available(now + 0.005)

        drain_end = time.perf_counter() + self.args.drain_seconds
        while time.perf_counter() < drain_end:
            self.receive_available(time.perf_counter() + 0.02)

    def summary(self):
        ready_count = sum(1 for client in self.clients if client.ready)
        rejected_count = sum(1 for client in self.clients if client.rejected)
        expected_relays = self.command_sends * max(0, ready_count - 1)
        delivered_pct = (self.command_relays / expected_relays * 100.0) if expected_relays else 0.0
        duration = max(0.001, self.args.duration)
        lat = self.latencies_ms
        return {
            "server": f"{self.args.server}:{self.args.port}",
            "clients_requested": self.args.clients,
            "clients_ready": ready_count,
            "clients_rejected": rejected_count,
            "duration_seconds": self.args.duration,
            "transform_rate_per_client": self.args.transform_rate,
            "command_rate_per_client": self.args.command_rate,
            "transforms_sent": self.transform_sends,
            "commands_sent": self.command_sends,
            "heartbeats_sent": self.heartbeat_sends,
            "sent_transforms_per_second": round(self.transform_sends / duration, 2),
            "sent_commands_per_second": round(self.command_sends / duration, 2),
            "expected_command_relays": expected_relays,
            "received_command_relays": self.command_relays,
            "duplicate_command_relays": self.duplicate_relays,
            "command_relay_delivery_pct": round(delivered_pct, 2),
            "latency_ms": {
                "count": len(lat),
                "avg": round(statistics.fmean(lat), 3) if lat else 0.0,
                "p50": round(percentile(lat, 50), 3),
                "p95": round(percentile(lat, 95), 3),
                "p99": round(percentile(lat, 99), 3),
                "max": round(max(lat), 3) if lat else 0.0,
            },
            "received_packets_by_type": {str(key): value for key, value in sorted(self.recv_by_type.items())},
        }


def print_summary(summary):
    print("\nSceneMax multiplayer load test")
    print("=" * 34)
    print(f"Server:                  {summary['server']}")
    print(f"Clients ready/rejected:  {summary['clients_ready']}/{summary['clients_rejected']}")
    print(f"Duration:                {summary['duration_seconds']}s")
    print(f"Transforms sent:         {summary['transforms_sent']} ({summary['sent_transforms_per_second']}/s)")
    print(f"Commands sent:           {summary['commands_sent']} ({summary['sent_commands_per_second']}/s)")
    print(f"Command relays:          {summary['received_command_relays']} / {summary['expected_command_relays']} "
          f"({summary['command_relay_delivery_pct']}%)")
    print(f"Duplicate relays:        {summary['duplicate_command_relays']}")
    latency = summary["latency_ms"]
    print("Relay latency ms:        "
          f"avg={latency['avg']} p50={latency['p50']} p95={latency['p95']} "
          f"p99={latency['p99']} max={latency['max']} count={latency['count']}")
    print(f"Received by type:        {summary['received_packets_by_type']}")


def parse_args():
    parser = argparse.ArgumentParser(description="Load test a SceneMax UDP multiplayer server.")
    parser.add_argument("--server", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=9001)
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--project-guid", default=DEFAULT_PROJECT_GUID)
    parser.add_argument("--password", default="")
    parser.add_argument("--clients", type=int, default=16)
    parser.add_argument("--duration", type=float, default=20.0)
    parser.add_argument("--stage-timeout", type=float, default=10.0)
    parser.add_argument("--drain-seconds", type=float, default=1.0)
    parser.add_argument("--session-id", type=int, default=1000)
    parser.add_argument("--session-name", default="load-test")
    parser.add_argument("--create-session", action="store_true")
    parser.add_argument("--scene", default="main")
    parser.add_argument("--archetype", default="sinbad")
    parser.add_argument("--transform-rate", type=float, default=20.0,
                        help="Transform corrections per ready client per second.")
    parser.add_argument("--command-rate", type=float, default=1.0,
                        help="Latency marker commands per ready client per second.")
    parser.add_argument("--motion-radius", type=float, default=3.0)
    parser.add_argument("--json-output", default="tools/multiplayer-server/load-test-last.json")
    return parser.parse_args()


def main():
    args = parse_args()
    random.seed(12345)
    test = LoadTest(args)
    try:
        print(f"Logging in {args.clients} simulated clients...")
        test.wait_for_logins()
        print("Creating one entity per ready client...")
        test.wait_for_creates()
        ready = sum(1 for client in test.clients if client.ready)
        rejected = sum(1 for client in test.clients if client.rejected)
        print(f"Ready clients: {ready}, rejected: {rejected}")
        test.run_load()
        summary = test.summary()
    finally:
        test.close()

    print_summary(summary)
    if args.json_output:
        output = Path(args.json_output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(summary, indent=2), encoding="utf-8")
        print(f"\nWrote JSON summary: {output}")


if __name__ == "__main__":
    main()
