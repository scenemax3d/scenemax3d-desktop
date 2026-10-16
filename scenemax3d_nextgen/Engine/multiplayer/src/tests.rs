use crate::{client::*, protocol::*, *};
use std::{net::UdpSocket, time::Duration};

fn packet(kind: u8, sender: u16, body: &[u8]) -> Vec<u8> {
    let mut p = Vec::new();
    write_header(kind, sender, &mut p);
    p.extend(body);
    p
}
fn fixed(out: &mut Vec<u8>, text: &str, n: usize) {
    out.extend(text.as_bytes());
    out.resize(out.len() + n - text.len(), 0);
}
fn spawn() -> Spawn {
    Spawn {
        archetype: "box".into(),
        player: "test".into(),
        source_name: "fixture".into(),
        command: "{network_entity} => box: size (1,2,3)".into(),
    }
}
fn login() -> Login {
    Login {
        password_hash: password_hash(""),
        project_guid: "test".into(),
        create_session: false,
        session_id: 1000,
        session_name: "test".into(),
        scene: "main".into(),
        player: "test".into(),
    }
}

#[test]
fn login_and_create_match_java_offsets() {
    let p = encode(0, &Outgoing::Login(login())).unwrap();
    assert_eq!(p.len(), 365);
    assert_eq!(&p[8..40], &password_hash(""));
    assert_eq!(&p[105..109], &1000u32.to_le_bytes());
    assert_eq!(&p[173..177], b"main");
    let p = encode(
        7,
        &Outgoing::Create {
            request: 42,
            spawn: spawn(),
        },
    )
    .unwrap();
    assert_eq!(p.len(), 460);
    assert_eq!(&p[8..12], &42u32.to_le_bytes());
    assert_eq!(&p[140..147], b"fixture");
    assert_eq!(&p[204..220], b"{network_entity}");
}

#[test]
fn snapshots_retain_split_actions_and_elapsed_time() {
    let mut b = 1u16.to_le_bytes().to_vec();
    b.extend(101u32.to_le_bytes());
    b.extend(9u16.to_le_bytes());
    fixed(&mut b, "box", 64);
    fixed(&mut b, "remote", 64);
    fixed(&mut b, "fixture", 64);
    for v in [1f32, 2., 3., 0., 0., 0., 1.] {
        b.extend(v.to_le_bytes());
    }
    b.extend(0u16.to_le_bytes());
    fixed(&mut b, "", 64);
    fixed(&mut b, &spawn().command, 256);
    b.push(1);
    b.push(1);
    b.push(0);
    b.extend(42u16.to_le_bytes());
    b.extend(700u32.to_le_bytes());
    b.extend(1000u32.to_le_bytes());
    fixed(&mut b, "{network_entity}.move forward 3 in 1 seconds", 192);
    let p = packet(SNAPSHOT, 0, &b);
    let (_, Incoming::Snapshot(entities)) = decode(&p, 7).unwrap() else {
        panic!()
    };
    assert_eq!(entities.len(), 1);
    assert_eq!(entities[0].pose.position, [1., 2., 3.]);
    assert_eq!(entities[0].actions[0].remaining_ms, 700);
    assert_eq!(entities[0].actions[0].action.duration_ms, 1000);
    for n in 0..p.len() {
        assert!(decode(&p[..n], 7).is_err(), "truncation {n}");
    }
}

#[test]
fn values_events_and_corrupt_packets() {
    for value in [
        serde_json::json!(null),
        serde_json::json!(false),
        serde_json::json!(12.5),
        serde_json::json!("שלום"),
        serde_json::json!([1, "x", [true, null]]),
    ] {
        assert_eq!(decode_value(&encode_value(&value).unwrap()).unwrap(), value);
        let p = encode(
            3,
            &Outgoing::Variable {
                name: "value".into(),
                initial: true,
                value: value.clone(),
            },
        )
        .unwrap();
        assert_eq!(
            decode(&p, 4).unwrap().1,
            Incoming::Variable {
                name: "value".into(),
                initial: true,
                value
            }
        );
    }
    let p = packet(NETWORK_EVENT, 5, "event\0שלום".as_bytes());
    assert_eq!(
        decode(&p, 4).unwrap().1,
        Incoming::Event {
            name: "event".into(),
            message: Some("שלום".into())
        }
    );
    assert!(
        encode(
            0,
            &Outgoing::Command {
                entity: 1,
                command: "x".repeat(1200)
            }
        )
        .is_err()
    );
    assert!(encode_value(&serde_json::json!("x".repeat(256))).is_err());
    assert!(decode_value(r#"{"type":"number","value":"wrong"}"#).is_err());
    assert!(decode(&packet(SNAPSHOT, 0, &65535u16.to_le_bytes()), 1).is_err());
    for n in 0..1201 {
        let mut p = vec![0xff; n];
        if n >= 8 {
            p[..8].copy_from_slice(&packet(SNAPSHOT, 0, &[]));
        }
        let _ = decode(&p, 1);
    }
}

#[test]
fn socket_handshake_ack_retry_queue_destroy_and_drop() {
    let server = UdpSocket::bind("127.0.0.1:0").unwrap();
    server
        .set_read_timeout(Some(Duration::from_secs(2)))
        .unwrap();
    let mut client = Client::connect(server.local_addr().unwrap(), login()).unwrap();
    let mut b = [0u8; 1201];
    let (_, addr) = server.recv_from(&mut b).unwrap();
    let request = client.register(spawn()).unwrap();
    client
        .send(
            Some(request),
            Outgoing::Command {
                entity: 0,
                command: "{network_entity}.move forward 1".into(),
            },
        )
        .unwrap();
    let mut accepted = 7u16.to_le_bytes().to_vec();
    accepted.extend(1000u32.to_le_bytes());
    fixed(&mut accepted, "test", 64);
    server
        .send_to(&packet(LOGIN_ACCEPTED, 7, &accepted), addr)
        .unwrap();
    server
        .send_to(&packet(INITIAL_SYNC_COMPLETE, 7, &[]), addr)
        .unwrap();
    for _ in 0..100 {
        client.poll().unwrap();
        if client.state() == State::Ready {
            break;
        }
        std::thread::sleep(Duration::from_millis(2));
    }
    let (n, _) = server.recv_from(&mut b).unwrap();
    assert_eq!(
        read_header(&b[..n]).unwrap().packet_type,
        CREATE_ENTITY_REQUEST
    );
    assert_eq!(&b[8..12], &request.to_le_bytes());
    std::thread::sleep(Duration::from_millis(360));
    client.poll().unwrap();
    let (n, _) = server.recv_from(&mut b).unwrap();
    assert_eq!(
        read_header(&b[..n]).unwrap().packet_type,
        CREATE_ENTITY_REQUEST
    );
    assert_eq!(&b[8..12], &request.to_le_bytes());
    let mut ack = 101u32.to_le_bytes().to_vec();
    ack.extend(request.to_le_bytes());
    server
        .send_to(&packet(CREATE_ENTITY_ACCEPTED, 7, &ack), addr)
        .unwrap();
    for _ in 0..100 {
        client.poll().unwrap();
        if client.network_id(request).is_some() {
            break;
        }
        std::thread::sleep(Duration::from_millis(2));
    }
    let (n, _) = server.recv_from(&mut b).unwrap();
    assert_eq!(read_header(&b[..n]).unwrap().packet_type, COMMAND_DISPATCH);
    assert_eq!(&b[8..12], &101u32.to_le_bytes());
    client.destroy(request).unwrap();
    let (n, _) = server.recv_from(&mut b).unwrap();
    assert_eq!(read_header(&b[..n]).unwrap().packet_type, DESTROY_ENTITY);
    drop(client);
    let (n, _) = server.recv_from(&mut b).unwrap();
    assert_eq!(read_header(&b[..n]).unwrap().packet_type, DISCONNECT);
}
