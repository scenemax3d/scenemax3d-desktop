//! Version-one wire contract shared with the Java client and Zig relay.
//! Fixed strings are NUL padded UTF-8; overlong outgoing values fail rather than
//! truncating executable source or JSON into a different command/value.
use crate::*;
use serde_json::Value;

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error(transparent)]
    Header(#[from] PacketError),
    #[error("malformed multiplayer packet: {0}")]
    Invalid(&'static str),
}

#[derive(Debug, Clone, PartialEq)]
pub struct Login {
    pub password_hash: [u8; 32],
    pub project_guid: String,
    pub create_session: bool,
    pub session_id: u32,
    pub session_name: String,
    pub scene: String,
    pub player: String,
}

#[derive(Debug, Clone, PartialEq)]
pub struct Spawn {
    pub archetype: String,
    pub player: String,
    pub source_name: String,
    pub command: String,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Pose {
    pub position: [f32; 3],
    /// Java Quaternion component order: x, y, z, w.
    pub rotation: [f32; 4],
}

impl Default for Pose {
    fn default() -> Self {
        Self {
            position: [0.; 3],
            rotation: [0., 0., 0., 1.],
        }
    }
}

#[derive(Debug, Clone, PartialEq)]
pub struct Action {
    pub slot: u8,
    pub sequence: u16,
    pub duration_ms: u32,
    pub command: String,
}

#[derive(Debug, Clone, PartialEq)]
pub struct SnapshotAction {
    pub action: Action,
    /// u32::MAX denotes a persistent action, not a finite duration.
    pub remaining_ms: u32,
}

#[derive(Debug, Clone, PartialEq)]
pub struct SnapshotEntity {
    pub id: u32,
    pub owner: u16,
    pub spawn: Spawn,
    pub pose: Pose,
    pub animation_index: u16,
    pub animation: String,
    pub actions: Vec<SnapshotAction>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Session {
    pub id: u32,
    pub clients: u16,
    pub name: String,
}

/// Outbound packets. Entity creation request IDs make retries idempotent on Zig.
#[derive(Debug, Clone, PartialEq)]
pub enum Outgoing {
    ActionEndReceipt { entity: u32, slot: u8, sequence: u16 },
    Login(Login),
    Heartbeat,
    Disconnect,
    JoinScene(String),
    JoinSession {
        id: u32,
        name: String,
    },
    Create {
        request: u32,
        spawn: Spawn,
    },
    Destroy(u32),
    Command {
        entity: u32,
        command: String,
    },
    Transform {
        entity: u32,
        pose: Pose,
    },
    ActionStart {
        entity: u32,
        action: Action,
    },
    ActionEnd {
        entity: u32,
        slot: u8,
        sequence: u16,
    },
    Event {
        target: u16,
        name: String,
        message: Option<String>,
    },
    Variable {
        name: String,
        initial: bool,
        value: Value,
    },
    EntityData {
        entity: u32,
        field: String,
        value: Value,
    },
    ServerEvent {
        name: String,
        interval_ms: u32,
    },
}

/// All messages emitted by the dedicated server. Snapshot chunks retain action
/// elapsed time and can contain the same entity in multiple datagrams.
#[derive(Debug, Clone, PartialEq)]
pub enum Incoming {
    ActionEndReceipt { entity: u32, slot: u8, sequence: u16 },
    Accepted {
        client: u16,
        session: u32,
        name: String,
    },
    Rejected,
    Created {
        entity: u32,
        request: u32,
    },
    RemoteCreated {
        entity: u32,
        spawn: Spawn,
    },
    Destroyed(u32),
    Command {
        entity: u32,
        command: String,
    },
    Transform {
        entity: u32,
        pose: Pose,
    },
    ActionStart {
        entity: u32,
        action: Action,
    },
    ActionEnd {
        entity: u32,
        slot: u8,
        sequence: u16,
    },
    Event {
        name: String,
        message: Option<String>,
    },
    Variable {
        name: String,
        initial: bool,
        value: Value,
    },
    EntityData {
        entity: u32,
        field: String,
        value: Value,
    },
    Snapshot(Vec<SnapshotEntity>),
    Sessions {
        total: u16,
        offset: u16,
        sessions: Vec<Session>,
    },
    Ready,
    Heartbeat,
    Disconnected,
}

pub fn encode(client: u16, message: &Outgoing) -> Result<Vec<u8>, Error> {
    let mut w = Writer(Vec::with_capacity(MAX_PACKET_SIZE));
    let kind = match message {
        Outgoing::ActionEndReceipt { .. } => ACTION_END_RECEIPT,
        Outgoing::Login(_) => LOGIN_REQUEST,
        Outgoing::Heartbeat => HEARTBEAT,
        Outgoing::Disconnect => DISCONNECT,
        Outgoing::JoinScene(_) => JOIN_SCENE,
        Outgoing::JoinSession { .. } => JOIN_SESSION,
        Outgoing::Create { .. } => CREATE_ENTITY_REQUEST,
        Outgoing::Destroy(_) => DESTROY_ENTITY,
        Outgoing::Command { .. } => COMMAND_DISPATCH,
        Outgoing::Transform { .. } => TRANSFORM_CORRECTION,
        Outgoing::ActionStart { .. } => ACTIVE_ACTION_START,
        Outgoing::ActionEnd { .. } => ACTIVE_ACTION_END,
        Outgoing::Event { .. } => NETWORK_EVENT,
        Outgoing::Variable { .. } => NETWORK_VARIABLE_UPDATE,
        Outgoing::EntityData { .. } => NETWORK_ENTITY_DATA_UPDATE,
        Outgoing::ServerEvent { .. } => SERVER_EVENT_REGISTER,
    };
    write_header(kind, client, &mut w.0);
    match message {
        Outgoing::Login(l) => {
            w.0.extend_from_slice(&l.password_hash);
            w.fixed(&l.project_guid, 64)?;
            w.byte(u8::from(l.create_session));
            w.u32(l.session_id);
            w.fixed(&l.session_name, 64)?;
            w.fixed(&l.scene, 128)?;
            w.fixed(&l.player, 64)?;
        }
        Outgoing::Heartbeat | Outgoing::Disconnect => {}
        Outgoing::JoinScene(scene) => w.fixed(scene, 128)?,
        Outgoing::JoinSession { id, name } => {
            w.u32(*id);
            w.fixed(name, 64)?;
        }
        Outgoing::Create { request, spawn } => {
            w.u32(*request);
            w.spawn(spawn)?;
        }
        Outgoing::Destroy(id) => w.u32(*id),
        Outgoing::Command { entity, command } => {
            w.u32(*entity);
            w.text(command)?;
        }
        Outgoing::Transform { entity, pose } => {
            w.u32(*entity);
            w.pose(pose)?;
        }
        Outgoing::ActionStart { entity, action } => {
            w.u32(*entity);
            w.byte(action.slot);
            w.byte(0);
            w.u16(action.sequence);
            w.u32(action.duration_ms);
            w.fixed(&action.command, 192)?;
        }
        Outgoing::ActionEndReceipt { entity, slot, sequence } | Outgoing::ActionEnd {
            entity,
            slot,
            sequence,
        } => {
            w.u32(*entity);
            w.byte(*slot);
            w.byte(0);
            w.u16(*sequence);
        }
        Outgoing::Event {
            target,
            name,
            message,
        } => {
            w.u16(*target);
            w.text(name)?;
            if let Some(message) = message {
                w.byte(0);
                w.text(message)?;
            }
        }
        Outgoing::Variable {
            name,
            initial,
            value,
        } => {
            w.fixed(name, 64)?;
            w.byte(u8::from(*initial));
            w.fixed(&encode_value(value)?, 256)?;
        }
        Outgoing::EntityData {
            entity,
            field,
            value,
        } => {
            w.u32(*entity);
            w.fixed(field, 64)?;
            w.fixed(&encode_value(value)?, 256)?;
        }
        Outgoing::ServerEvent { name, interval_ms } => {
            if *interval_ms == 0 {
                return Err(Error::Invalid("zero event interval"));
            }
            w.fixed(name, 64)?;
            w.u32(*interval_ms);
        }
    }
    if w.0.len() > MAX_PACKET_SIZE {
        return Err(Error::Invalid("packet exceeds 1200 bytes"));
    }
    Ok(w.0)
}

pub fn decode(packet: &[u8], local_client: u16) -> Result<(u16, Incoming), Error> {
    if packet.len() > MAX_PACKET_SIZE {
        return Err(Error::Invalid("oversized packet"));
    }
    let header = read_header(packet)?;
    let mut r = Reader(&packet[8..]);
    let message = match header.packet_type {
        LOGIN_ACCEPTED => Incoming::Accepted {
            client: r.u16()?,
            session: r.u32()?,
            name: r.fixed(64)?,
        },
        LOGIN_REJECTED => Incoming::Rejected,
        HEARTBEAT => Incoming::Heartbeat,
        DISCONNECT => Incoming::Disconnected,
        INITIAL_SYNC_COMPLETE => Incoming::Ready,
        CREATE_ENTITY_ACCEPTED if header.client_id == local_client => Incoming::Created {
            entity: r.u32()?,
            request: r.u32()?,
        },
        CREATE_ENTITY_ACCEPTED => Incoming::RemoteCreated {
            entity: r.u32()?,
            spawn: r.spawn()?,
        },
        DESTROY_ENTITY => Incoming::Destroyed(r.u32()?),
        COMMAND_DISPATCH => Incoming::Command {
            entity: r.u32()?,
            command: r.tail()?,
        },
        TRANSFORM_CORRECTION => Incoming::Transform {
            entity: r.u32()?,
            pose: r.pose()?,
        },
        ACTIVE_ACTION_START => {
            let entity = r.u32()?;
            let slot = r.byte()?;
            r.byte()?;
            Incoming::ActionStart {
                entity,
                action: Action {
                    slot,
                    sequence: r.u16()?,
                    duration_ms: r.u32()?,
                    command: r.fixed(192)?,
                },
            }
        }
        ACTIVE_ACTION_END | ACTION_END_RECEIPT => {
            if header.packet_type == ACTION_END_RECEIPT && packet.len() != 16 {
                return Err(Error::Invalid("action end receipt size"));
            }
            let entity = r.u32()?;
            let slot = r.byte()?;
            r.byte()?;
            let sequence = r.u16()?;
            if slot == 0 { return Err(Error::Invalid("zero action end slot")); }
            if header.packet_type == ACTION_END_RECEIPT {
                Incoming::ActionEndReceipt { entity, slot, sequence }
            } else { Incoming::ActionEnd {
                entity,
                slot,
                sequence,
            } }
        }
        NETWORK_EVENT => {
            let bytes = r.take(r.0.len())?;
            let (name, message) = match bytes.iter().position(|v| *v == 0) {
                Some(n) => (utf8(&bytes[..n])?, Some(utf8(&bytes[n + 1..])?)),
                None => (utf8(bytes)?, None),
            };
            Incoming::Event { name, message }
        }
        NETWORK_VARIABLE_UPDATE => Incoming::Variable {
            name: r.fixed(64)?,
            initial: r.byte()? != 0,
            value: decode_value(&r.fixed(256)?)?,
        },
        NETWORK_ENTITY_DATA_UPDATE => Incoming::EntityData {
            entity: r.u32()?,
            field: r.fixed(64)?,
            value: decode_value(&r.fixed(256)?)?,
        },
        SNAPSHOT => {
            let count = r.u16()?;
            if usize::from(count) > r.0.len() / 549 {
                return Err(Error::Invalid("snapshot count"));
            }
            let mut entities = Vec::with_capacity(usize::from(count));
            for _ in 0..count {
                let id = r.u32()?;
                let owner = r.u16()?;
                let archetype = r.fixed(64)?;
                let player = r.fixed(64)?;
                let source_name = r.fixed(64)?;
                let pose = r.pose()?;
                let animation_index = r.u16()?;
                let animation = r.fixed(64)?;
                let command = r.fixed(256)?;
                let count = r.byte()?;
                if usize::from(count) > r.0.len() / 204 {
                    return Err(Error::Invalid("snapshot action count"));
                }
                let mut actions = Vec::with_capacity(usize::from(count));
                for _ in 0..count {
                    let slot = r.byte()?;
                    r.byte()?;
                    let sequence = r.u16()?;
                    let remaining_ms = r.u32()?;
                    let duration_ms = r.u32()?;
                    let command = r.fixed(192)?;
                    actions.push(SnapshotAction {
                        action: Action {
                            slot,
                            sequence,
                            duration_ms,
                            command,
                        },
                        remaining_ms,
                    });
                }
                entities.push(SnapshotEntity {
                    id,
                    owner,
                    spawn: Spawn {
                        archetype,
                        player,
                        source_name,
                        command,
                    },
                    pose,
                    animation_index,
                    animation,
                    actions,
                });
            }
            Incoming::Snapshot(entities)
        }
        SERVER_STATE => {
            let total = r.u16()?;
            let offset = r.u16()?;
            let count = r.u16()?;
            if total > 256
                || u32::from(offset) + u32::from(count) > u32::from(total)
                || usize::from(count) > r.0.len() / 70
            {
                return Err(Error::Invalid("session counts"));
            }
            let mut sessions = Vec::with_capacity(usize::from(count));
            for _ in 0..count {
                sessions.push(Session {
                    id: r.u32()?,
                    clients: r.u16()?,
                    name: r.fixed(64)?,
                });
            }
            Incoming::Sessions {
                total,
                offset,
                sessions,
            }
        }
        _ => return Err(Error::Invalid("unknown server packet type")),
    };
    if !r.0.is_empty() {
        return Err(Error::Invalid("trailing payload"));
    }
    Ok((header.client_id, message))
}

fn valid_value(value: &Value) -> bool {
    match value {
        Value::Object(_) => false,
        Value::Array(a) => a.iter().all(valid_value),
        _ => true,
    }
}

pub fn encode_value(value: &Value) -> Result<String, Error> {
    if !valid_value(value) {
        return Err(Error::Invalid("unsupported shared value"));
    }
    let kind = match value {
        Value::Null => "null",
        Value::Bool(_) => "boolean",
        Value::Number(_) => "number",
        Value::String(_) => "string",
        Value::Array(_) => "array",
        Value::Object(_) => unreachable!(),
    };
    let encoded = serde_json::json!({"type":kind,"value":value}).to_string();
    if encoded.len() >= 256 {
        return Err(Error::Invalid("shared value exceeds 255 bytes"));
    }
    Ok(encoded)
}

pub fn decode_value(text: &str) -> Result<Value, Error> {
    let json: Value =
        serde_json::from_str(text).map_err(|_| Error::Invalid("invalid value JSON"))?;
    let value = json
        .get("value")
        .ok_or(Error::Invalid("missing shared value"))?;
    let valid = match json.get("type").and_then(Value::as_str) {
        Some("null") => value.is_null(),
        Some("boolean") => value.is_boolean(),
        Some("number") => value.is_number(),
        Some("string") => value.is_string(),
        Some("array") => value.is_array() && valid_value(value),
        _ => false,
    };
    if !valid {
        return Err(Error::Invalid("shared value type mismatch"));
    }
    Ok(value.clone())
}

struct Writer(Vec<u8>);
impl Writer {
    fn byte(&mut self, v: u8) {
        self.0.push(v);
    }
    fn u16(&mut self, v: u16) {
        self.0.extend(v.to_le_bytes());
    }
    fn u32(&mut self, v: u32) {
        self.0.extend(v.to_le_bytes());
    }
    fn text(&mut self, s: &str) -> Result<(), Error> {
        if s.contains('\0') {
            return Err(Error::Invalid("embedded NUL"));
        }
        self.0.extend(s.as_bytes());
        Ok(())
    }
    fn fixed(&mut self, s: &str, size: usize) -> Result<(), Error> {
        if s.len() >= size {
            return Err(Error::Invalid("fixed string overflow"));
        }
        self.text(s)?;
        self.0.resize(self.0.len() + size - s.len(), 0);
        Ok(())
    }
    fn spawn(&mut self, s: &Spawn) -> Result<(), Error> {
        self.fixed(&s.archetype, 64)?;
        self.fixed(&s.player, 64)?;
        self.fixed(&s.source_name, 64)?;
        self.fixed(&s.command, 256)
    }
    fn pose(&mut self, p: &Pose) -> Result<(), Error> {
        for v in p.position.iter().chain(p.rotation.iter()) {
            if !v.is_finite() {
                return Err(Error::Invalid("nonfinite transform"));
            }
            self.u32(v.to_bits());
        }
        Ok(())
    }
}
struct Reader<'a>(&'a [u8]);
impl<'a> Reader<'a> {
    fn take(&mut self, n: usize) -> Result<&'a [u8], Error> {
        if n > self.0.len() {
            return Err(Error::Invalid("truncated payload"));
        }
        let (a, b) = self.0.split_at(n);
        self.0 = b;
        Ok(a)
    }
    fn byte(&mut self) -> Result<u8, Error> {
        Ok(self.take(1)?[0])
    }
    fn u16(&mut self) -> Result<u16, Error> {
        let b = self.take(2)?;
        Ok(u16::from_le_bytes([b[0], b[1]]))
    }
    fn u32(&mut self) -> Result<u32, Error> {
        let b = self.take(4)?;
        Ok(u32::from_le_bytes([b[0], b[1], b[2], b[3]]))
    }
    fn fixed(&mut self, n: usize) -> Result<String, Error> {
        let b = self.take(n)?;
        utf8(&b[..b.iter().position(|v| *v == 0).unwrap_or(n)])
    }
    fn tail(&mut self) -> Result<String, Error> {
        utf8(self.take(self.0.len())?)
    }
    fn spawn(&mut self) -> Result<Spawn, Error> {
        Ok(Spawn {
            archetype: self.fixed(64)?,
            player: self.fixed(64)?,
            source_name: self.fixed(64)?,
            command: self.fixed(256)?,
        })
    }
    fn pose(&mut self) -> Result<Pose, Error> {
        let mut values = [0.; 7];
        for v in &mut values {
            *v = f32::from_bits(self.u32()?);
            if !v.is_finite() {
                return Err(Error::Invalid("nonfinite transform"));
            }
        }
        Ok(Pose {
            position: [values[0], values[1], values[2]],
            rotation: [values[3], values[4], values[5], values[6]],
        })
    }
}
fn utf8(bytes: &[u8]) -> Result<String, Error> {
    String::from_utf8(bytes.to_vec()).map_err(|_| Error::Invalid("invalid UTF-8"))
}
