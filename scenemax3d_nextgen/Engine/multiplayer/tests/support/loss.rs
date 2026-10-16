//! Deterministic datagram faults, pumped on the test thread (no orphan workers).
use super::*;
use scenemax_multiplayer::*;
use std::{collections::VecDeque, net::SocketAddr};

struct Proxy {
    front: UdpSocket,
    back: UdpSocket,
    peer: Option<SocketAddr>,
    drops: VecDeque<(bool, u8)>,
    dropped: Vec<(bool, u8)>,
    blackhole: Option<bool>,
}
impl Proxy {
    fn new(server: SocketAddr) -> Self {
        let front = UdpSocket::bind("127.0.0.1:0").unwrap();
        let back = UdpSocket::bind("127.0.0.1:0").unwrap();
        back.connect(server).unwrap();
        front.set_nonblocking(true).unwrap();
        back.set_nonblocking(true).unwrap();
        Self {
            front,
            back,
            peer: None,
            drops: VecDeque::new(),
            dropped: Vec::new(),
            blackhole: None,
        }
    }
    fn address(&self) -> SocketAddr {
        self.front.local_addr().unwrap()
    }
    fn drop_next(&mut self, outbound: bool, kind: u8) {
        self.drops.push_back((outbound, kind));
    }
    fn discard(&mut self, outbound: bool, bytes: &[u8]) -> bool {
        let key = (outbound, read_header(bytes).unwrap().packet_type);
        if self.blackhole == Some(outbound) {
            self.dropped.push(key);
            return true;
        }
        if let Some(index) = self.drops.iter().position(|rule| *rule == key) {
            self.drops.remove(index);
            self.dropped.push(key);
            true
        } else {
            false
        }
    }
    fn pump(&mut self) {
        let mut bytes = [0; MAX_PACKET_SIZE + 1];
        for _ in 0..128 {
            match self.front.recv_from(&mut bytes) {
                Ok((size, peer)) => {
                    self.peer = Some(peer);
                    if !self.discard(true, &bytes[..size]) {
                        self.back.send(&bytes[..size]).unwrap();
                    }
                }
                Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => break,
                Err(e) => panic!("proxy upstream: {e}"),
            }
        }
        for _ in 0..128 {
            match self.back.recv(&mut bytes) {
                Ok(size) => {
                    if !self.discard(false, &bytes[..size]) {
                        self.front
                            .send_to(&bytes[..size], self.peer.unwrap())
                            .unwrap();
                    }
                }
                Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => break,
                Err(e) => panic!("proxy downstream: {e}"),
            }
        }
    }
    fn until(
        &mut self,
        client: &mut Client,
        predicate: impl Fn(&Incoming) -> bool,
    ) -> Vec<Incoming> {
        let deadline = Instant::now() + Duration::from_secs(4);
        let mut all = Vec::new();
        while Instant::now() < deadline {
            self.pump();
            all.extend(client.poll().unwrap().into_iter().map(|e| e.message));
            if all.iter().any(&predicate) {
                return all;
            }
            std::thread::sleep(Duration::from_millis(2));
        }
        panic!("proxy deadline; dropped={:?}; events={all:?}", self.dropped);
    }
    fn settle(&mut self, client: &mut Client, duration: Duration) {
        let deadline = Instant::now() + duration;
        while Instant::now() < deadline {
            self.pump();
            client.poll().unwrap();
            std::thread::sleep(Duration::from_millis(2));
        }
    }
}

fn partition_closes_connection(outbound: bool) {
    let (_temp, _server, address) = start_server();
    let mut proxy = Proxy::new(address);
    let mut client = Client::connect(proxy.address(), login()).unwrap();
    proxy.until(&mut client, |e| matches!(e, Incoming::Ready));
    // Lose the first echo. A retry must succeed without surfacing the reserved
    // transport event to application handlers or disconnecting the client.
    proxy.drop_next(false, NETWORK_EVENT);
    let healthy_until = Instant::now() + Duration::from_secs(4);
    while Instant::now() < healthy_until {
        proxy.pump();
        assert!(
            client
                .poll()
                .unwrap()
                .iter()
                .all(|event| !matches!(event.message, Incoming::Event { .. }))
        );
        std::thread::sleep(Duration::from_millis(5));
    }
    assert!(proxy.drops.is_empty());
    assert_eq!(client.state(), State::Ready);
    proxy.blackhole = Some(outbound);
    let deadline = Instant::now() + Duration::from_secs(13);
    loop {
        proxy.pump();
        match client.poll() {
            Ok(_) => assert!(Instant::now() < deadline, "partition left the client ready"),
            Err(error) => {
                assert!(
                    matches!(error, scenemax_multiplayer::client::Error::Io(ref e) if e.kind() == std::io::ErrorKind::TimedOut)
                );
                break;
            }
        }
        std::thread::sleep(Duration::from_millis(5));
    }
    assert_eq!(client.state(), State::Closed);
    assert!(client.poll().unwrap().is_empty());
    assert!(
        proxy
            .dropped
            .iter()
            .any(|(direction, kind)| *direction == outbound && *kind == NETWORK_EVENT)
    );
}

#[test]
#[ignore = "set SCENEMAX_TEST_MP_SERVER to the repository Zig server executable"]
fn upstream_partition_is_detected_after_a_lost_probe_recovers() {
    partition_closes_connection(true);
}

#[test]
#[ignore = "set SCENEMAX_TEST_MP_SERVER to the repository Zig server executable"]
fn downstream_partition_is_detected_after_a_lost_probe_recovers() {
    partition_closes_connection(false);
}

#[test]
#[ignore = "set SCENEMAX_TEST_MP_SERVER to the repository Zig server executable"]
fn numbered_ends_recover_both_hops_and_both_lost_receipts_without_duplicate_delivery() {
    let (_temp, _server, address) = start_server();
    let mut owner_proxy = Proxy::new(address);
    let mut owner = Client::connect(owner_proxy.address(), login()).unwrap();
    owner_proxy.until(&mut owner, |e| matches!(e, Incoming::Ready));
    let request = owner
        .register(Spawn {
            archetype: "box".into(),
            player: "fixture".into(),
            source_name: "subject".into(),
            command: "{network_entity} => box".into(),
        })
        .unwrap();
    owner_proxy.until(&mut owner, |e| matches!(e, Incoming::Created { .. }));
    let id = owner.network_id(request).unwrap();
    let mut receiver_proxy = Proxy::new(address);
    let mut receiver = Client::connect(receiver_proxy.address(), login()).unwrap();
    receiver_proxy.until(&mut receiver, |e| matches!(e, Incoming::Ready));
    for (sequence, expires_in_queue) in [(u16::MAX, false), (0, true)] {
        owner_proxy.drop_next(true, ACTIVE_ACTION_END);
        owner_proxy.drop_next(false, ACTION_END_RECEIPT);
        receiver_proxy.drop_next(false, ACTIVE_ACTION_END);
        receiver_proxy.drop_next(true, ACTION_END_RECEIPT);
        if expires_in_queue {
            owner
                .send(
                    Some(request),
                    Outgoing::ActionStart {
                        entity: 0,
                        action: Action {
                            slot: 3,
                            sequence,
                            duration_ms: 1,
                            command: "{network_entity}.take".into(),
                        },
                    },
                )
                .unwrap();
            std::thread::sleep(Duration::from_millis(10));
        } else {
            owner
                .send(
                    Some(request),
                    Outgoing::ActionEnd {
                        entity: 0,
                        slot: 3,
                        sequence,
                    },
                )
                .unwrap();
        }
        let deadline = Instant::now() + Duration::from_secs(2);
        let mut ends = 0;
        while Instant::now() < deadline {
            owner_proxy.pump();
            receiver_proxy.pump();
            assert!(
                !owner
                    .poll()
                    .unwrap()
                    .iter()
                    .any(|e| matches!(e.message, Incoming::ActionEndReceipt { .. }))
            );
            for event in receiver.poll().unwrap() {
                if let Incoming::ActionEnd {
                    entity,
                    slot: 3,
                    sequence: seq,
                } = event.message
                {
                    assert_eq!((entity, seq), (id, sequence));
                    ends += 1;
                }
                assert!(!matches!(
                    event.message,
                    Incoming::ActionEndReceipt { .. } | Incoming::ActionStart { .. }
                ));
            }
            std::thread::sleep(Duration::from_millis(3));
        }
        assert!(
            owner_proxy.drops.is_empty() && receiver_proxy.drops.is_empty(),
            "all four faults must fire"
        );
        assert_eq!(
            ends, 1,
            "relay retry must not produce duplicate application delivery"
        );
        assert_eq!(owner.expired_action_ends(), 0);
    }
    receiver.join(Outgoing::JoinScene("main".into())).unwrap();
    let snapshot = receiver_proxy.until(&mut receiver, |e| matches!(e, Incoming::Ready));
    assert!(snapshot.iter().any(|e| matches!(e, Incoming::Snapshot(r) if r.iter().any(|r| r.id == id && r.actions.is_empty()))));
}

#[test]
#[ignore = "set SCENEMAX_TEST_MP_SERVER to the repository Zig server executable"]
fn dropped_scene_and_same_session_joins_do_not_report_ready() {
    let (_temp, _server, address) = start_server();
    let mut proxy = Proxy::new(address);
    let mut client = Client::connect(proxy.address(), login()).unwrap();
    proxy.until(&mut client, |e| matches!(e, Incoming::Ready));
    for message in [
        Outgoing::JoinScene("main".into()),
        Outgoing::JoinSession {
            id: 1000,
            name: String::new(),
        },
    ] {
        let kind = if matches!(message, Outgoing::JoinScene(_)) {
            JOIN_SCENE
        } else {
            JOIN_SESSION
        };
        proxy.drop_next(true, kind);
        client.join(message.clone()).unwrap();
        proxy.settle(&mut client, Duration::from_millis(300));
        assert!(proxy.drops.is_empty(), "fault must actually fire");
        assert_eq!(
            client.state(),
            State::Synchronizing,
            "lost join cannot succeed from silence"
        );
        // Explicit retry only: session creation and scene transitions are not
        // safe to retransmit blindly under protocol v1.
        client.join(message).unwrap();
        proxy.until(&mut client, |e| matches!(e, Incoming::Ready));
        assert_eq!(client.state(), State::Ready);
    }
}

#[test]
#[ignore = "set SCENEMAX_TEST_MP_SERVER to the repository Zig server executable"]
fn dropped_transition_acceptance_or_ready_requires_explicit_retry() {
    let (_temp, _server, address) = start_server();
    let mut proxy = Proxy::new(address);
    proxy.drop_next(false, INITIAL_SYNC_COMPLETE);
    let mut client = Client::connect(proxy.address(), login()).unwrap();
    proxy.settle(&mut client, Duration::from_millis(300));
    assert!(proxy.drops.is_empty());
    assert_eq!(
        client.state(),
        State::Ready,
        "initial login keeps its bounded quiet fallback"
    );
    for kind in [LOGIN_ACCEPTED, INITIAL_SYNC_COMPLETE] {
        let destination = client.session_id() + 1;
        let message = Outgoing::JoinSession {
            id: destination,
            name: String::new(),
        };
        proxy.drop_next(false, kind);
        client.join(message.clone()).unwrap();
        proxy.settle(&mut client, Duration::from_millis(300));
        assert!(proxy.drops.is_empty());
        assert_eq!(client.state(), State::Synchronizing);
        client.join(message).unwrap();
        proxy.until(&mut client, |e| matches!(e, Incoming::Ready));
        assert_eq!(client.state(), State::Ready);
        assert_eq!(client.session_id(), destination);
    }
}

#[test]
#[ignore = "set SCENEMAX_TEST_MP_SERVER to the repository Zig server executable"]
fn dropped_create_ack_snapshot_and_end_recover_at_explicit_rejoin() {
    let (_temp, _server, address) = start_server();
    let mut owner_proxy = Proxy::new(address);
    let mut owner = Client::connect(owner_proxy.address(), login()).unwrap();
    owner_proxy.until(&mut owner, |e| matches!(e, Incoming::Ready));
    owner_proxy.drop_next(true, CREATE_ENTITY_REQUEST);
    owner_proxy.drop_next(false, CREATE_ENTITY_ACCEPTED);
    let request = owner
        .register(Spawn {
            archetype: "box".into(),
            player: "fixture-client".into(),
            source_name: "subject".into(),
            command: "{network_entity} => box: size (1,2,3)".into(),
        })
        .unwrap();
    owner_proxy.until(&mut owner, |e| matches!(e, Incoming::Created { .. }));
    assert!(owner_proxy.drops.is_empty());
    let id = owner.network_id(request).unwrap();
    owner
        .send(
            Some(request),
            Outgoing::ActionStart {
                entity: 0,
                action: Action {
                    slot: 3,
                    sequence: 1,
                    duration_ms: 60000,
                    command: "{network_entity}.take loop".into(),
                },
            },
        )
        .unwrap();
    owner
        .send(
            None,
            Outgoing::Variable {
                name: "fixture-state".into(),
                initial: false,
                value: serde_json::json!(42),
            },
        )
        .unwrap();
    owner
        .send(
            None,
            Outgoing::Event {
                target: owner.client_id(),
                name: "stored".into(),
                message: None,
            },
        )
        .unwrap();
    owner_proxy.until(
        &mut owner,
        |e| matches!(e, Incoming::Event { name, .. } if name == "stored"),
    );

    let mut proxy = Proxy::new(address);
    proxy.drop_next(false, SNAPSHOT);
    let mut receiver = Client::connect(proxy.address(), login()).unwrap();
    let lost = proxy.until(&mut receiver, |e| matches!(e, Incoming::Ready));
    assert!(proxy.drops.is_empty());
    assert!(!lost.iter().any(|e| matches!(e, Incoming::Snapshot(_))));
    // Ready is not an acknowledgement of every snapshot chunk. Rejoin obtains
    // the authoritative state that was lost; no general loss recovery is claimed.
    receiver.join(Outgoing::JoinScene("main".into())).unwrap();
    let restored = proxy.until(&mut receiver, |e| matches!(e, Incoming::Ready));
    let records = restored
        .iter()
        .filter_map(|e| {
            if let Incoming::Snapshot(r) = e {
                Some(r)
            } else {
                None
            }
        })
        .flatten()
        .collect::<Vec<_>>();
    assert_eq!(
        records.iter().filter(|r| r.id == id).count(),
        1,
        "create retry must not duplicate entities"
    );
    assert!(records.iter().any(|r| {
        r.id == id
            && r.actions
                .iter()
                .any(|a| a.action.sequence == 1 && a.action.slot == 3)
    }));
    assert!(restored.iter().any(|e| matches!(e, Incoming::Variable { name, value, .. } if name == "fixture-state" && value == &serde_json::json!(42))));
    proxy.drop_next(false, ACTIVE_ACTION_END);
    owner
        .send(
            Some(request),
            Outgoing::ActionEnd {
                entity: 0,
                slot: 3,
                sequence: 1,
            },
        )
        .unwrap();
    owner
        .send(
            None,
            Outgoing::Event {
                target: receiver.client_id(),
                name: "ended".into(),
                message: None,
            },
        )
        .unwrap();
    owner.poll().unwrap();
    owner_proxy.pump();
    let lost_end = proxy.until(
        &mut receiver,
        |e| matches!(e, Incoming::Event { name, .. } if name == "ended"),
    );
    assert!(proxy.drops.is_empty());
    assert!(
        !lost_end
            .iter()
            .any(|e| matches!(e, Incoming::ActionEnd { .. }))
    );
    receiver.join(Outgoing::JoinScene("main".into())).unwrap();
    let ended = proxy.until(&mut receiver, |e| matches!(e, Incoming::Ready));
    assert!(ended.iter().any(|e| matches!(e, Incoming::Snapshot(r) if r.iter().any(|r| r.id == id && r.actions.is_empty()))));
    // Loss before the server is a different boundary: a rejoin cannot recover
    // an end the authority never received. A later explicitly sent end can.
    owner
        .send(
            Some(request),
            Outgoing::ActionStart {
                entity: 0,
                action: Action {
                    slot: 3,
                    sequence: 2,
                    duration_ms: 60000,
                    command: "{network_entity}.take loop".into(),
                },
            },
        )
        .unwrap();
    owner
        .send(
            None,
            Outgoing::Event {
                target: owner.client_id(),
                name: "second-start".into(),
                message: None,
            },
        )
        .unwrap();
    owner_proxy.until(
        &mut owner,
        |e| matches!(e, Incoming::Event { name, .. } if name == "second-start"),
    );
    owner_proxy.drop_next(true, ACTIVE_ACTION_END);
    let end = Outgoing::ActionEnd {
        entity: 0,
        slot: 3,
        sequence: 2,
    };
    owner.send(Some(request), end.clone()).unwrap();
    owner
        .send(
            None,
            Outgoing::Event {
                target: owner.client_id(),
                name: "end-lost".into(),
                message: None,
            },
        )
        .unwrap();
    owner_proxy.until(
        &mut owner,
        |e| matches!(e, Incoming::Event { name, .. } if name == "end-lost"),
    );
    assert!(owner_proxy.drops.is_empty());
    receiver.join(Outgoing::JoinScene("main".into())).unwrap();
    let still_active = proxy.until(&mut receiver, |e| matches!(e, Incoming::Ready));
    assert!(still_active.iter().any(|e| matches!(e, Incoming::Snapshot(r) if r.iter().any(|r| r.id == id && r.actions.iter().any(|a| a.action.sequence == 2)))));
    owner.send(Some(request), end).unwrap();
    owner
        .send(
            None,
            Outgoing::Event {
                target: owner.client_id(),
                name: "end-repaired".into(),
                message: None,
            },
        )
        .unwrap();
    owner_proxy.until(
        &mut owner,
        |e| matches!(e, Incoming::Event { name, .. } if name == "end-repaired"),
    );
    let previous_client = receiver.client_id();
    receiver.close();
    proxy.pump();
    let mut replacement_proxy = Proxy::new(address);
    let mut replacement = Client::connect(replacement_proxy.address(), login()).unwrap();
    let fresh = replacement_proxy.until(&mut replacement, |e| matches!(e, Incoming::Ready));
    assert_ne!(replacement.client_id(), previous_client);
    assert!(fresh.iter().any(|e| matches!(e, Incoming::Snapshot(r) if r.iter().any(|r| r.id == id && r.actions.is_empty()))));
}
