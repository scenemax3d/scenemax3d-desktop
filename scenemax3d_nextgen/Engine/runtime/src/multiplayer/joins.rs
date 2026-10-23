//! Script join completion is separate from transport readiness.
use super::*;
use serde_json::Value;

pub(super) enum Selector {
    Id(u32),
    Name(String),
}
impl Selector {
    pub(super) fn parse(value: Value) -> Option<Self> {
        match value {
            Value::Number(n) => n
                .as_f64()
                .filter(|n| {
                    n.is_finite() && *n >= 0. && *n <= f64::from(u32::MAX) && n.fract() == 0.
                })
                .map(|n| Self::Id(n as u32)),
            Value::String(name) if !name.trim().is_empty() => {
                Some(Self::Name(name.trim().to_owned()))
            }
            _ => None,
        }
    }
    pub(super) fn message(&self) -> Outgoing {
        match self {
            Self::Id(id) => Outgoing::JoinSession {
                id: *id,
                name: String::new(),
            },
            Self::Name(name) => Outgoing::JoinSession {
                id: 0,
                name: name.clone(),
            },
        }
    }
    fn matches(&self, id: u32, name: &str, previous: u32) -> bool {
        match self {
            Self::Id(0) => id != 0 && id != previous,
            Self::Id(expected) => id == *expected,
            Self::Name(expected) => name == expected,
        }
    }
}

#[derive(Default)]
pub(super) struct JoinState {
    active: Option<(Selector, u32, Instant)>,
    error: String,
}
impl JoinState {
    pub(super) fn begin(&mut self, selector: Selector, previous: u32, now: Instant) {
        self.active = Some((selector, previous, now));
        self.error.clear();
    }
    pub(super) fn fail(&mut self, error: &str) {
        self.active = None;
        self.error = error.into();
    }
    pub(super) fn reject_overlap(&mut self) {
        self.error = "session join already pending".into();
    }
    pub(super) fn pending(&self) -> bool {
        self.active.is_some()
    }
    pub(super) fn error(&self) -> &str {
        &self.error
    }
    pub(super) fn update(&mut self, client: Option<&Client>, now: Instant) {
        let Some((selector, previous, started)) = &self.active else {
            return;
        };
        let Some(client) = client else {
            self.fail("offline");
            return;
        };
        if matches!(client.state(), State::Rejected | State::Closed) {
            self.fail("connection unavailable");
        } else if client.state() == State::Ready
            && selector.matches(client.session_id(), client.session_name(), *previous)
        {
            self.active = None;
            self.error.clear();
        } else if now.saturating_duration_since(*started) >= Duration::from_secs(10) {
            self.fail("session join timed out");
        }
    }
}

#[cfg(test)]
pub(super) mod tests {
    use super::*;
    use std::net::{SocketAddr, UdpSocket};
    pub(crate) fn connection() -> (UdpSocket, SocketAddr, Client) {
        let server = UdpSocket::bind("127.0.0.1:0").unwrap();
        server
            .set_read_timeout(Some(Duration::from_secs(1)))
            .unwrap();
        let client = Client::connect(
            server.local_addr().unwrap(),
            Login {
                password_hash: password_hash(""),
                project_guid: "fixture".into(),
                create_session: false,
                session_id: 1000,
                session_name: "initial".into(),
                scene: "main".into(),
                player: "fixture".into(),
            },
        )
        .unwrap();
        let (_, peer) = server.recv_from(&mut [0; 1200]).unwrap();
        (server, peer, client)
    }
    pub(crate) fn accept(
        server: &UdpSocket,
        peer: SocketAddr,
        client: &mut Client,
        id: u32,
        name: &str,
    ) {
        let mut packet = Vec::new();
        scenemax_multiplayer::write_header(scenemax_multiplayer::LOGIN_ACCEPTED, 0, &mut packet);
        packet.extend(1u16.to_le_bytes());
        packet.extend(id.to_le_bytes());
        packet.extend(name.as_bytes());
        packet.resize(78, 0);
        server.send_to(&packet, peer).unwrap();
        let deadline = Instant::now() + Duration::from_secs(1);
        while client.session_id() != id {
            client.poll().unwrap();
            assert!(Instant::now() < deadline);
        }
        let mut ready = Vec::new();
        scenemax_multiplayer::write_header(
            scenemax_multiplayer::INITIAL_SYNC_COMPLETE,
            0,
            &mut ready,
        );
        server.send_to(&ready, peer).unwrap();
        while client.state() != State::Ready {
            client.poll().unwrap();
            assert!(Instant::now() < deadline);
        }
    }
    #[test]
    fn requested_room_identity_timeout_and_disconnect_control_join_completion() {
        let (server, peer, mut client) = connection();
        accept(&server, peer, &mut client, 1000, "initial");
        let now = Instant::now();
        let mut join = JoinState::default();
        join.begin(Selector::Id(2000), 1000, now);
        join.update(Some(&client), now);
        assert!(join.pending()); // Ready in old room must not release the script.
        join.update(Some(&client), now + Duration::from_secs(10));
        assert!(!join.pending());
        assert_eq!(join.error(), "session join timed out");
        join.begin(Selector::Id(2000), 1000, now);
        client.join(Selector::Id(2000).message()).unwrap();
        accept(&server, peer, &mut client, 2000, "next");
        join.update(Some(&client), now);
        assert!(!join.pending());
        assert!(join.error().is_empty());
        join.begin(
            Selector::parse(Value::String("  named  ".into())).unwrap(),
            2000,
            now,
        );
        join.update(Some(&client), now);
        assert!(join.pending());
        client
            .join(Selector::Name("named".into()).message())
            .unwrap();
        accept(&server, peer, &mut client, 3000, "named");
        join.update(Some(&client), now);
        assert!(!join.pending());
        join.begin(Selector::Id(0), 3000, now);
        join.update(Some(&client), now);
        assert!(join.pending());
        client.close();
        join.update(Some(&client), now);
        assert!(!join.pending());
        assert_eq!(join.error(), "connection unavailable");
    }
    fn capture_join(world: &mut World, source: &str) {
        let action = scenemax_parser::parse_program(source)
            .unwrap()
            .statements
            .remove(0);
        let mut vars = world.remove_resource::<SceneMaxVars>().unwrap();
        events::capture(
            &mut world.commands(),
            &action,
            &mut vars,
            None,
            &HashMap::new(),
            &HashMap::new(),
        );
        assert_eq!(vars.0["network.joinPending"], 1.);
        world.insert_resource(vars);
        world.flush();
        update(world);
    }
    #[test]
    fn dropped_same_room_join_keeps_script_waiting_until_server_confirmation() {
        let (server, peer, mut client) = connection();
        accept(&server, peer, &mut client, 1000, "initial");
        let mut world = World::new();
        world.init_resource::<SceneMaxVars>();
        world.insert_resource(Multiplayer {
            client: Some(client),
            ..Default::default()
        });
        capture_join(&mut world, "network.join session (1000)");
        // The socket fixture receives but never answers the join. A matching
        // old session ID plus the former quiet fallback must not release it.
        std::thread::sleep(Duration::from_millis(200));
        update(&mut world);
        assert_eq!(
            world.resource::<SceneMaxVars>().0["network.joinPending"],
            1.
        );
        assert_eq!(world.resource::<SceneMaxVars>().0["network.ready"], 0.);
        let mut net = world.remove_resource::<Multiplayer>().unwrap();
        accept(&server, peer, net.client.as_mut().unwrap(), 1000, "initial");
        world.insert_resource(net);
        update(&mut world);
        assert_eq!(
            world.resource::<SceneMaxVars>().0["network.joinPending"],
            0.
        );
        assert_eq!(world.resource::<SceneMaxVars>().0["network.ready"], 1.);
    }
    #[test]
    fn offline_invalid_and_unresolved_joins_release_wait_without_remote_cleanup() {
        for (source, error) in [
            ("network.join session (2000)", "offline"),
            ("network.join session (-1)", "invalid session selector"),
            ("network.join session (null)", "invalid session selector"),
            ("network.join session (unknown)", "invalid session selector"),
            ("network.join session (1.5)", "invalid session selector"),
            ("network.join session (\"\")", "invalid session selector"),
        ] {
            let mut world = World::new();
            world.init_resource::<SceneMaxVars>();
            let mut net = Multiplayer::default();
            net.remotes.insert(9, "fixture_remote".into());
            world.insert_resource(net);
            capture_join(&mut world, source);
            let vars = world.resource::<SceneMaxVars>();
            assert_eq!(vars.0["network.joinPending"], 0., "{source}");
            assert_eq!(vars.0["network.ready"], 0.);
            assert_eq!(
                scenemax_runtime_vm_core::typed::lookup("network.joinError", vars, None),
                Some(Value::String(error.into()))
            );
            assert!(world.resource::<Multiplayer>().remotes.contains_key(&9));
        }
    }
    #[test]
    fn overlapping_join_preserves_the_first_destination_and_deadline() {
        let (server, peer, mut client) = connection();
        accept(&server, peer, &mut client, 1000, "initial");
        let mut world = World::new();
        world.init_resource::<SceneMaxVars>();
        world.insert_resource(Multiplayer {
            client: Some(client),
            ..Default::default()
        });
        capture_join(&mut world, "network.join session (2000)");
        capture_join(&mut world, "network.join session (3000)");
        let net = world.resource::<Multiplayer>();
        assert!(events::join_pending(&net.events));
        assert_eq!(
            events::join_error(&net.events),
            "session join already pending"
        );
        let mut net = world.remove_resource::<Multiplayer>().unwrap();
        accept(&server, peer, net.client.as_mut().unwrap(), 2000, "first");
        events::update_join(&mut net, Instant::now());
        assert!(!events::join_pending(&net.events));
        assert!(events::join_error(&net.events).is_empty());
    }
    #[test]
    fn queued_join_during_login_releases_after_rejection() {
        let (server, peer, client) = connection();
        let mut world = World::new();
        world.init_resource::<SceneMaxVars>();
        world.insert_resource(Multiplayer {
            client: Some(client),
            ..Default::default()
        });
        capture_join(&mut world, "network.join session (2000)");
        assert_eq!(
            world.resource::<SceneMaxVars>().0["network.joinPending"],
            1.
        );
        let mut packet = Vec::new();
        scenemax_multiplayer::write_header(scenemax_multiplayer::LOGIN_REJECTED, 0, &mut packet);
        server.send_to(&packet, peer).unwrap();
        let deadline = Instant::now() + Duration::from_secs(1);
        while world.resource::<SceneMaxVars>().0["network.joinPending"] != 0. {
            update(&mut world);
            assert!(Instant::now() < deadline);
        }
        assert_eq!(world.resource::<SceneMaxVars>().0["network.ready"], 0.);
    }
}
