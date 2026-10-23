//! Opt-in compatibility gate against the actual repository Zig executable.
use scenemax_multiplayer::{
    client::{Client, State, password_hash},
    protocol::*,
};
use std::{
    net::UdpSocket,
    process::{Child, Command, Stdio},
    time::{Duration, Instant},
};

struct Server(Child);
impl Drop for Server {
    fn drop(&mut self) {
        let _ = self.0.kill();
        let _ = self.0.wait();
    }
}
fn login() -> Login {
    Login {
        password_hash: password_hash("fixture-password"),
        project_guid: "fixture-guid".into(),
        create_session: false,
        session_id: 1000,
        session_name: "fixture-session".into(),
        scene: "main".into(),
        player: "fixture-client".into(),
    }
}
fn until(client: &mut Client, predicate: impl Fn(&Incoming) -> bool) -> Vec<Incoming> {
    let deadline = Instant::now() + Duration::from_secs(4);
    let mut all = Vec::new();
    while Instant::now() < deadline {
        for event in client.poll().unwrap() {
            let done = predicate(&event.message);
            all.push(event.message);
            if done {
                return all;
            }
        }
        std::thread::sleep(Duration::from_millis(5));
    }
    panic!("timed out; events={all:?}");
}

#[test]
#[ignore = "set SCENEMAX_TEST_MP_SERVER to the repository Zig server executable"]
fn real_zig_server_large_lobby_pages_and_named_id_joins() {
    let (_temp, _server, address) = start_server();
    let mut creator = Client::connect(address, login()).unwrap();
    until(&mut creator, |e| matches!(e, Incoming::Ready));
    for index in 1..=80 {
        creator
            .join(Outgoing::JoinSession {
                id: 0,
                name: format!("lobby-{index:03}"),
            })
            .unwrap();
        until(&mut creator, |e| matches!(e, Incoming::Ready));
        assert_eq!(creator.session_id(), 1000 + index);
        assert_eq!(creator.sessions().count(), index as usize + 1);
    }
    let mut observer = Client::connect(address, login()).unwrap();
    let events = until(&mut observer, |e| matches!(e, Incoming::Ready));
    let pages: Vec<_> = events
        .iter()
        .filter_map(|event| match event {
            Incoming::Sessions {
                total,
                offset,
                sessions,
            } => Some((*total, *offset, sessions.len())),
            _ => None,
        })
        .collect();
    assert_eq!(
        pages.len(),
        6,
        "81 records require six bounded UDP pages: {pages:?}"
    );
    assert!(pages.iter().all(|page| page.0 == 81));
    assert_eq!(pages.iter().map(|page| page.2).sum::<usize>(), 81);
    assert_eq!(observer.sessions().count(), 81);
    for index in 1..=80 {
        let room = observer
            .sessions()
            .find(|room| room.id == 1000 + index)
            .unwrap();
        assert_eq!(room.name, format!("lobby-{index:03}"));
        assert_eq!(room.clients, u16::from(index == 80));
    }
    observer
        .join(Outgoing::JoinSession {
            id: 0,
            name: "lobby-037".into(),
        })
        .unwrap();
    until(&mut observer, |e| matches!(e, Incoming::Ready));
    assert_eq!(observer.session_id(), 1037);
    assert_eq!(
        observer.sessions().count(),
        81,
        "named join must reuse the discovered room"
    );
    assert_eq!(
        observer
            .sessions()
            .find(|room| room.id == 1037)
            .unwrap()
            .clients,
        1
    );
    observer
        .join(Outgoing::JoinSession {
            id: 1080,
            name: String::new(),
        })
        .unwrap();
    until(&mut observer, |e| matches!(e, Incoming::Ready));
    assert_eq!(observer.session_name(), "lobby-080");
    assert_eq!(
        observer
            .sessions()
            .find(|room| room.id == 1080)
            .unwrap()
            .clients,
        2
    );
    assert_eq!(
        observer
            .sessions()
            .find(|room| room.id == 1037)
            .unwrap()
            .clients,
        0
    );
}

#[test]
#[ignore = "set SCENEMAX_TEST_MP_SERVER to the repository Zig server executable"]
fn real_zig_server_two_clients_late_join_state_events_and_scene_isolation() {
    let (_temp, _server, address) = start_server();
    let mut bad = login();
    bad.password_hash = password_hash("wrong");
    let mut rejected = Client::connect(address, bad).unwrap();
    until(&mut rejected, |e| matches!(e, Incoming::Rejected));
    assert_eq!(rejected.state(), State::Rejected);
    let mut a = Client::connect(address, login()).unwrap();
    until(&mut a, |e| matches!(e, Incoming::Ready));
    let request = a
        .register(Spawn {
            archetype: "box".into(),
            player: "fixture-client".into(),
            source_name: "fixture".into(),
            command: "{network_entity} => box: size (1,2,3)".into(),
        })
        .unwrap();
    until(&mut a, |e| matches!(e, Incoming::Created { .. }));
    let id = a.network_id(request).unwrap();
    let pose = Pose {
        position: [2., 3., 4.],
        ..Default::default()
    };
    a.send(Some(request), Outgoing::Transform { entity: 0, pose })
        .unwrap();
    a.send(
        None,
        Outgoing::Variable {
            name: "shared".into(),
            initial: true,
            value: serde_json::json!([1, "text", [true, null]]),
        },
    )
    .unwrap();
    a.send(
        Some(request),
        Outgoing::EntityData {
            entity: 0,
            field: "score".into(),
            value: serde_json::json!(42),
        },
    )
    .unwrap();
    a.send(
        Some(request),
        Outgoing::ActionStart {
            entity: 0,
            action: Action {
                slot: 64,
                sequence: 1,
                duration_ms: u32::MAX,
                command: "{network_entity}.shader = \"fixture\"".into(),
            },
        },
    )
    .unwrap();
    a.poll().unwrap();
    std::thread::sleep(Duration::from_millis(50));
    // Independent widget slots survive snapshotting together. Payloads remain
    // ordinary typed variables; the runtime restricts their application to UI.
    let widget_prefix = "$ui:0123456789abcdef0123456789abcdef";
    let widget_values = [
        ("text", serde_json::json!("שלום UI")),
        (
            "visible",
            serde_json::json!([a.client_id(), 1, "quad", "left", 2.0]),
        ),
        ("frame", serde_json::json!("3")),
        ("textcolor", serde_json::json!("#00ff00")),
        ("backgroundcolor", serde_json::json!("#203050")),
        ("fontsize", serde_json::json!("42")),
    ];
    for (slot, value) in &widget_values {
        a.send(
            None,
            Outgoing::Variable {
                name: format!("{widget_prefix}:{slot}"),
                initial: false,
                value: value.clone(),
            },
        )
        .unwrap();
    }
    a.poll().unwrap();
    std::thread::sleep(Duration::from_millis(50));
    let mut b = Client::connect(address, login()).unwrap();
    let snapshot = until(&mut b, |e| matches!(e, Incoming::Ready));
    for (slot, expected) in &widget_values {
        assert!(snapshot.iter().any(
            |event| matches!(event, Incoming::Variable { name, value, .. }
            if name == &format!("{widget_prefix}:{slot}") && value == expected)
        ));
    }
    a.send(
        None,
        Outgoing::Variable {
            name: format!("{widget_prefix}:visible"),
            initial: false,
            value: serde_json::json!(false),
        },
    )
    .unwrap();
    let widget_live = barrier(&mut a, &mut b, "widget-hide");
    assert!(
        widget_live
            .iter()
            .any(|event| matches!(event, Incoming::Variable {name,value,..}
        if name == &format!("{widget_prefix}:visible") && value == &serde_json::json!(false)))
    );
    assert!(snapshot.iter().any(|e|matches!(e,Incoming::Snapshot(v) if v.iter().any(|s|s.id==id && s.pose==pose && s.actions.iter().any(|a|a.action.slot==64)))));
    assert!(snapshot.iter().any(|e|matches!(e,Incoming::Variable {name,value,..} if name=="shared" && value==&serde_json::json!([1,"text",[true,null]]))));
    assert!(snapshot.iter().any(
        |e| matches!(e,Incoming::EntityData {entity,field,..} if *entity==id && field=="score")
    ));
    // Numbered action starts must be idempotent at the server as well as at
    // receivers, otherwise duplicates restart the late-join duration clock.
    for sequence in [u16::MAX, u16::MAX, 0, u16::MAX] {
        a.send(
            Some(request),
            Outgoing::ActionStart {
                entity: 0,
                action: Action {
                    slot: 65,
                    sequence,
                    duration_ms: u32::MAX,
                    command: "{network_entity}.pos = (1,2,3)".into(),
                },
            },
        )
        .unwrap();
        a.poll().unwrap(); // Exercise relay filtering, independently of local coalescing.
    }
    let relayed = barrier(&mut a, &mut b, "start-order");
    let sequences = relayed
        .iter()
        .filter_map(|message| match message {
            Incoming::ActionStart { entity, action } if *entity == id && action.slot == 65 => {
                Some(action.sequence)
            }
            _ => None,
        })
        .collect::<Vec<_>>();
    assert_eq!(
        sequences,
        [u16::MAX, 0],
        "duplicate/stale structural starts must not be relayed"
    );
    a.send(
        Some(request),
        Outgoing::ActionEnd {
            entity: 0,
            slot: 65,
            sequence: u16::MAX,
        },
    )
    .unwrap();
    let stale_end = barrier(&mut a, &mut b, "stale-end");
    assert!(
        !stale_end
            .iter()
            .any(|e| matches!(e, Incoming::ActionEnd { .. }))
    );
    b.join(Outgoing::JoinScene("main".into())).unwrap();
    let wrapped_snapshot = until(&mut b, |e| matches!(e, Incoming::Ready));
    assert!(wrapped_snapshot.iter().any(|e| matches!(e, Incoming::Snapshot(records)
        if records.iter().any(|r| r.id == id && r.actions.iter().any(|a| a.action.slot == 65 && a.action.sequence == 0)))));

    // End can overtake its start; both that start and older retransmissions
    // must stay absent from the server's subsequent snapshot.
    a.send(
        Some(request),
        Outgoing::ActionEnd {
            entity: 0,
            slot: 65,
            sequence: 1,
        },
    )
    .unwrap();
    a.send(
        Some(request),
        Outgoing::ActionEnd {
            entity: 0,
            slot: 65,
            sequence: 1,
        },
    )
    .unwrap();
    for sequence in [0, 1] {
        a.send(
            Some(request),
            Outgoing::ActionStart {
                entity: 0,
                action: Action {
                    slot: 65,
                    sequence,
                    duration_ms: u32::MAX,
                    command: "{network_entity}.pos = (4,5,6)".into(),
                },
            },
        )
        .unwrap();
    }
    let ended = barrier(&mut a, &mut b, "ended-order");
    assert_eq!(ended.iter().filter(|e| matches!(e, Incoming::ActionEnd { entity, slot: 65, sequence: 1 } if *entity == id)).count(), 1);
    assert!(!ended.iter().any(|e| matches!(e, Incoming::ActionStart { entity, action } if *entity == id && action.slot == 65)));
    b.join(Outgoing::JoinScene("main".into())).unwrap();
    let ended_snapshot = until(&mut b, |e| matches!(e, Incoming::Ready));
    assert!(
        ended_snapshot.iter().any(
            |e| matches!(e, Incoming::Snapshot(records) if records.iter().any(|r| r.id == id))
        )
    );
    assert!(
        !ended_snapshot
            .iter()
            .any(|e| matches!(e, Incoming::Snapshot(records)
        if records.iter().any(|r| r.id == id && r.actions.iter().any(|a| a.action.slot == 65))))
    );
    // An unrelated client cannot end the owner's newer action.
    a.send(
        Some(request),
        Outgoing::ActionStart {
            entity: 0,
            action: Action {
                slot: 65,
                sequence: 2,
                duration_ms: u32::MAX,
                command: "{network_entity}.pos = (7,8,9)".into(),
            },
        },
    )
    .unwrap();
    barrier(&mut a, &mut b, "new-active");
    b.send(
        None,
        Outgoing::ActionEnd {
            entity: id,
            slot: 65,
            sequence: 3,
        },
    )
    .unwrap();
    let spoofed = barrier(&mut b, &mut a, "foreign-end");
    assert!(
        !spoofed
            .iter()
            .any(|e| matches!(e, Incoming::ActionEnd { .. }))
    );
    b.join(Outgoing::JoinScene("main".into())).unwrap();
    let active = until(&mut b, |e| matches!(e, Incoming::Ready));
    assert!(active.iter().any(|e| matches!(e, Incoming::Snapshot(records)
        if records.iter().any(|r| r.id == id && r.actions.iter().any(|a| a.action.slot == 65 && a.action.sequence == 2)))));
    // Live motion and looping animation use the same numbered start as late
    // join snapshots. Duplicate/stale packets must never execute a second time.
    for slot in [1, 2, 3] {
        for sequence in [u16::MAX, u16::MAX, 0, u16::MAX] {
            a.send(
                Some(request),
                Outgoing::ActionStart {
                    entity: 0,
                    action: Action {
                        slot,
                        sequence,
                        duration_ms: 10000,
                        command: "{network_entity}.move forward 10 in 10 seconds".into(),
                    },
                },
            )
            .unwrap();
            a.poll().unwrap();
        }
    }
    let live = barrier(&mut a, &mut b, "live-starts");
    for expected in [1, 2, 3] {
        let sequences = live
            .iter()
            .filter_map(|message| match message {
                Incoming::ActionStart { entity, action }
                    if *entity == id && action.slot == expected =>
                {
                    Some(action.sequence)
                }
                _ => None,
            })
            .collect::<Vec<_>>();
        assert_eq!(sequences, [u16::MAX, 0], "live slot {expected}");
    }
    assert!(!live.iter().any(|e| matches!(e, Incoming::Command { .. })));
    // Ownership checks apply to motion starts as well as structural starts.
    b.send(
        None,
        Outgoing::ActionStart {
            entity: id,
            action: Action {
                slot: 1,
                sequence: 1,
                duration_ms: 10000,
                command: "{network_entity}.move forward 999 in 10 seconds".into(),
            },
        },
    )
    .unwrap();
    let spoofed = barrier(&mut b, &mut a, "foreign-start");
    assert!(
        !spoofed
            .iter()
            .any(|e| matches!(e, Incoming::ActionStart { .. }))
    );
    b.join(Outgoing::JoinScene("main".into())).unwrap();
    let live_snapshot = until(&mut b, |e| matches!(e, Incoming::Ready));
    for expected in [1, 2, 3] {
        assert!(live_snapshot.iter().any(|e| matches!(e, Incoming::Snapshot(records)
            if records.iter().any(|r| r.id == id && r.actions.iter().any(|a| a.action.slot == expected && a.action.sequence == 0)))));
    }
    // Motion slots also deliver matching ends exactly once.
    for slot in [1, 65] {
        if slot == 1 {
            a.send(
                Some(request),
                Outgoing::ActionStart {
                    entity: 0,
                    action: Action {
                        slot,
                        sequence: 2,
                        duration_ms: 10000,
                        command: "{network_entity}.move forward 10 in 10 seconds".into(),
                    },
                },
            )
            .unwrap();
        }
        for _ in 0..2 {
            a.send(
                Some(request),
                Outgoing::ActionEnd {
                    entity: 0,
                    slot,
                    sequence: 2,
                },
            )
            .unwrap();
        }
    }
    let ends = barrier(&mut a, &mut b, "current-ends");
    for expected in [1, 65] {
        assert_eq!(ends.iter().filter(|e| matches!(e, Incoming::ActionEnd { entity, slot, sequence: 2 } if *entity == id && *slot == expected)).count(), 1);
    }
    // Backlogged replacements coalesce before the relay. Completed short
    // playback sends only an end, invalidating any previous action in its slot.
    for sequence in [10, 11] {
        a.send(
            Some(request),
            Outgoing::ActionStart {
                entity: 0,
                action: Action {
                    slot: 3,
                    sequence,
                    duration_ms: 1000,
                    command: "{network_entity}.take".into(),
                },
            },
        )
        .unwrap();
    }
    let coalesced = barrier(&mut a, &mut b, "coalesced");
    let starts = coalesced
        .iter()
        .filter_map(|e| match e {
            Incoming::ActionStart { action, .. } if action.slot == 3 => Some(action.sequence),
            _ => None,
        })
        .collect::<Vec<_>>();
    assert_eq!(starts, [11]);
    a.send(
        Some(request),
        Outgoing::ActionStart {
            entity: 0,
            action: Action {
                slot: 3,
                sequence: 12,
                duration_ms: 1,
                command: "{network_entity}.take".into(),
            },
        },
    )
    .unwrap();
    std::thread::sleep(Duration::from_millis(10));
    let expired = barrier(&mut a, &mut b, "expired-playback");
    assert!(
        !expired
            .iter()
            .any(|e| matches!(e,Incoming::ActionStart { action,.. } if action.slot==3))
    );
    assert!(expired.iter().any(|e| matches!(
        e,
        Incoming::ActionEnd {
            slot: 3,
            sequence: 12,
            ..
        }
    )));
    a.send(
        None,
        Outgoing::Event {
            target: b.client_id(),
            name: "message".into(),
            message: Some("שלום".into()),
        },
    )
    .unwrap();
    a.poll().unwrap();
    until(
        &mut b,
        |e| matches!(e,Incoming::Event {name,message} if name=="message" && message.as_deref()==Some("שלום")),
    );
    b.send(
        None,
        Outgoing::ServerEvent {
            name: "tick".into(),
            interval_ms: 100,
        },
    )
    .unwrap();
    b.poll().unwrap();
    // The Zig receive loop advances timers on traffic, like ordinary heartbeats.
    std::thread::sleep(Duration::from_millis(120));
    a.send(None, Outgoing::Heartbeat).unwrap();
    a.poll().unwrap();
    until(
        &mut b,
        |e| matches!(e,Incoming::Event {name,..} if name=="tick"),
    );
    b.join(Outgoing::JoinScene("separate".into())).unwrap();
    until(&mut b, |e| matches!(e, Incoming::Ready));
    a.send(
        None,
        Outgoing::Event {
            target: 0,
            name: "isolated".into(),
            message: None,
        },
    )
    .unwrap();
    a.poll().unwrap();
    std::thread::sleep(Duration::from_millis(40));
    assert!(
        !b.poll()
            .unwrap()
            .iter()
            .any(|e| matches!(&e.message,Incoming::Event {name,..} if name=="isolated"))
    );
    b.join(Outgoing::JoinScene("main".into())).unwrap();
    until(&mut b, |e| matches!(e, Incoming::Ready));
    // Shader bases precede material overrides in snapshots; replacing a
    // material with a shader removes the superseded persistent material slot.
    for shader_last in [true, false] {
        let material = Outgoing::ActionStart {
            entity: 0,
            action: Action {
                slot: 251,
                sequence: if shader_last { 1 } else { 2 },
                duration_ms: u32::MAX,
                command: "{network_entity}.material = \"finish\"".into(),
            },
        };
        let shader = Outgoing::EntityData {
            entity: 0,
            field: "shader".into(),
            value: serde_json::json!("base"),
        };
        let ordered = if shader_last {
            [material, shader]
        } else {
            [shader, material]
        };
        for update in ordered {
            a.send(Some(request), update).unwrap();
        }
        barrier(&mut a, &mut b, "surface-stored");
        b.join(Outgoing::JoinScene("main".into())).unwrap();
        let snapshot = until(&mut b, |e| matches!(e, Incoming::Ready));
        let base_index = snapshot
            .iter()
            .position(|e| {
                matches!(e, Incoming::EntityData { entity, field, .. }
            if *entity == id && field == "shader")
            })
            .unwrap();
        let history_index = snapshot.iter().position(|e| matches!(e, Incoming::Command { entity, command }
            if *entity == id && command.contains(".material = \"finish\"") && command.contains(".shader = \"base\""))).unwrap();
        assert!(history_index < base_index);
        let object_index = snapshot.iter().position(|e| matches!(e, Incoming::Snapshot(records) if records.iter().any(|record| record.id == id))).unwrap();
        assert!(base_index < object_index);
        let has_material = snapshot.iter().any(|e| matches!(e, Incoming::Snapshot(records)
            if records.iter().any(|record| record.id == id && record.actions.iter().any(|entry| entry.action.slot == 251))));
        assert_eq!(has_material, !shader_last);
    }
    a.destroy(request).unwrap();
    until(
        &mut b,
        |e| matches!(e,Incoming::Destroyed(entity) if *entity==id),
    );
    b.join(Outgoing::JoinSession {
        id: 2000,
        name: "other".into(),
    })
    .unwrap();
    until(&mut b, |e| matches!(e, Incoming::Ready));
    assert_eq!(b.session_id(), 2000);
}

// A server event behind queued packets provides a processing barrier without
// assuming a fixed scheduling delay on this loopback compatibility fixture.
fn barrier(a: &mut Client, b: &mut Client, name: &str) -> Vec<Incoming> {
    a.send(
        None,
        Outgoing::Event {
            target: b.client_id(),
            name: name.into(),
            message: None,
        },
    )
    .unwrap();
    a.poll().unwrap();
    until(
        b,
        |e| matches!(e, Incoming::Event { name: received, .. } if received == name),
    )
}

#[test]
#[ignore = "set SCENEMAX_TEST_MP_SERVER to the repository Zig server executable"]
fn real_zig_server_three_client_targeted_events_and_room_return() {
    let (_temp, _server, address) = start_server();
    let mut a = Client::connect(address, login()).unwrap();
    until(&mut a, |e| matches!(e, Incoming::Ready));
    let mut b = Client::connect(address, login()).unwrap();
    until(&mut b, |e| matches!(e, Incoming::Ready));
    let mut c = Client::connect(address, login()).unwrap();
    until(&mut c, |e| matches!(e, Incoming::Ready));
    a.send(
        None,
        Outgoing::Event {
            target: a.client_id(),
            name: "self".into(),
            message: Some("7".into()),
        },
    )
    .unwrap();
    until(
        &mut a,
        |e| matches!(e, Incoming::Event { name, message: Some(message) } if name == "self" && message == "7"),
    );
    a.send(
        None,
        Outgoing::Event {
            target: b.client_id(),
            name: "only_b".into(),
            message: Some("payload".into()),
        },
    )
    .unwrap();
    a.poll().unwrap();
    until(
        &mut b,
        |e| matches!(e, Incoming::Event { name, .. } if name == "only_b"),
    );
    let excluded = barrier(&mut a, &mut c, "before_leave");
    assert!(
        !excluded.iter().any(
            |e| matches!(e, Incoming::Event { name, .. } if name == "self" || name == "only_b")
        )
    );
    b.join(Outgoing::JoinSession {
        id: 2000,
        name: String::new(),
    })
    .unwrap();
    until(&mut b, |e| matches!(e, Incoming::Ready));
    a.send(
        None,
        Outgoing::Event {
            target: b.client_id(),
            name: "wrong_room".into(),
            message: None,
        },
    )
    .unwrap();
    a.poll().unwrap();
    b.send(
        None,
        Outgoing::Event {
            target: b.client_id(),
            name: "room_barrier".into(),
            message: None,
        },
    )
    .unwrap();
    let separate = until(
        &mut b,
        |e| matches!(e, Incoming::Event { name, .. } if name == "room_barrier"),
    );
    assert!(
        !separate
            .iter()
            .any(|e| matches!(e, Incoming::Event { name, .. } if name == "wrong_room"))
    );
    b.join(Outgoing::JoinSession {
        id: 1000,
        name: String::new(),
    })
    .unwrap();
    until(&mut b, |e| matches!(e, Incoming::Ready));
    a.send(
        None,
        Outgoing::Event {
            target: b.client_id(),
            name: "returned".into(),
            message: Some("current".into()),
        },
    )
    .unwrap();
    a.poll().unwrap();
    let returned = until(
        &mut b,
        |e| matches!(e, Incoming::Event { name, .. } if name == "returned"),
    );
    assert!(
        !returned
            .iter()
            .any(|e| matches!(e, Incoming::Event { name, .. } if name == "wrong_room"))
    );
    let excluded = barrier(&mut a, &mut c, "after_return");
    assert!(!excluded.iter().any(
        |e| matches!(e, Incoming::Event { name, .. } if name == "returned" || name == "wrong_room")
    ));
}

fn start_server() -> (tempfile::TempDir, Server, std::net::SocketAddr) {
    let template = std::env::var_os("SCENEMAX_TEST_MP_SERVER").expect("test server path");
    let temp = tempfile::tempdir().unwrap();
    let mut bytes = std::fs::read(template).unwrap();
    assert!(
        bytes
            .windows(b"SCENEMAX_MP_PORT".len())
            .any(|window| window == b"SCENEMAX_MP_PORT"),
        "Rebuild the Zig server templates for ephemeral-port tests"
    );
    let begin = b"SCENEMAX_MP_CONFIG_BEGIN";
    let end = b"SCENEMAX_MP_CONFIG_END";
    let matches = bytes
        .windows(begin.len())
        .enumerate()
        .filter(|(i, w)| {
            *w == begin
                && bytes.get(i + begin.len() + 4096..i + begin.len() + 4096 + end.len())
                    == Some(end.as_slice())
        })
        .map(|(i, _)| i + begin.len())
        .collect::<Vec<_>>();
    assert_eq!(matches.len(), 1);
    let start = matches[0];
    let payload = &mut bytes[start..start + 4096];
    payload.fill(0);
    payload[..8].copy_from_slice(b"SMXMPCFG");
    payload[8..12].copy_from_slice(&1u32.to_le_bytes());
    payload[12..16].copy_from_slice(&9001u32.to_le_bytes());
    payload[400..432].copy_from_slice(&password_hash("fixture-password"));
    payload[432..444].copy_from_slice(b"fixture-guid");
    let executable = temp.path().join(if cfg!(windows) {
        "server.exe"
    } else {
        "server"
    });
    std::fs::write(&executable, bytes).unwrap();
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        std::fs::set_permissions(&executable, std::fs::Permissions::from_mode(0o700)).unwrap();
    }
    let mut command = Command::new(&executable);
    command.env("SCENEMAX_MP_BIND_ADDRESS", "127.0.0.1");
    command.env("SCENEMAX_MP_PORT", "0");
    let log_path = temp.path().join("server.log");
    command
        .stdout(Stdio::null())
        .stderr(std::fs::File::create(&log_path).unwrap());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    let mut server = Server(command.spawn().unwrap());
    let deadline = Instant::now() + Duration::from_secs(10);
    let port: u16 = loop {
        assert!(
            server.0.try_wait().unwrap().is_none(),
            "fixture server exited; inspect {log_path:?}"
        );
        let log = std::fs::read_to_string(&log_path).unwrap();
        if let Some((_, suffix)) = log.split_once("server listening address=127.0.0.1 port=") {
            if let Some(value) = suffix
                .split_whitespace()
                .next()
                .and_then(|s| s.parse::<u16>().ok())
            {
                assert_ne!(value, 0);
                break value;
            }
        }
        assert!(
            Instant::now() < deadline,
            "fixture server startup timeout; inspect {log_path:?}"
        );
        std::thread::sleep(Duration::from_millis(20));
    };
    let address = format!("127.0.0.1:{port}").parse().unwrap();
    // Process creation is not socket readiness (especially during native builds).
    // Invalid credentials give a safe repeatable probe without allocating IDs.
    let probe = UdpSocket::bind("127.0.0.1:0").unwrap();
    probe
        .set_read_timeout(Some(Duration::from_millis(100)))
        .unwrap();
    let mut invalid = login();
    invalid.password_hash = password_hash("readiness-invalid");
    let packet = scenemax_multiplayer::protocol::encode(0, &Outgoing::Login(invalid)).unwrap();
    let deadline = Instant::now() + Duration::from_secs(10);
    loop {
        assert!(
            server.0.try_wait().unwrap().is_none(),
            "fixture server exited before binding"
        );
        probe.send_to(&packet, address).unwrap();
        let mut bytes = [0; 1200];
        if let Ok((size, from)) = probe.recv_from(&mut bytes)
            && from == address
            && matches!(
                scenemax_multiplayer::protocol::decode(&bytes[..size], 0),
                Ok((_, Incoming::Rejected))
            )
        {
            break;
        }
        assert!(
            Instant::now() < deadline,
            "fixture server did not become ready"
        );
    }
    (temp, server, address)
}

#[path = "support/loss.rs"]
mod loss;
