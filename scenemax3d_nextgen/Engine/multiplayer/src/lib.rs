pub const MAGIC: u32 = 0x504d5853;
pub const VERSION: u8 = 1;
pub const MAX_PACKET_SIZE: usize = 1200;

pub const LOGIN_REQUEST: u8 = 1;
pub const LOGIN_ACCEPTED: u8 = 2;
pub const LOGIN_REJECTED: u8 = 3;
pub const HEARTBEAT: u8 = 4;
pub const JOIN_SCENE: u8 = 5;
pub const JOIN_SESSION: u8 = 6;
pub const CREATE_ENTITY_REQUEST: u8 = 10;
pub const CREATE_ENTITY_ACCEPTED: u8 = 11;
pub const DESTROY_ENTITY: u8 = 12;
pub const COMMAND_DISPATCH: u8 = 20;
pub const TRANSFORM_CORRECTION: u8 = 21;
pub const ACTIVE_ACTION_START: u8 = 22;
pub const ACTIVE_ACTION_END: u8 = 23;
pub const NETWORK_EVENT: u8 = 24;
pub const NETWORK_VARIABLE_UPDATE: u8 = 25;
pub const SERVER_EVENT_REGISTER: u8 = 26;
pub const NETWORK_ENTITY_DATA_UPDATE: u8 = 27;
pub const SNAPSHOT: u8 = 30;
pub const SERVER_STATE: u8 = 31;
pub const INITIAL_SYNC_COMPLETE: u8 = 32;
pub const DISCONNECT: u8 = 40;

pub const MULTIPLAYER_ACTION_SLOT_MOVE: u16 = 1;
pub const MULTIPLAYER_ACTION_SLOT_ROTATE: u16 = 2;
pub const MULTIPLAYER_ACTION_SLOT_ANIMATE: u16 = 3;
pub const MULTIPLAYER_ACTION_SLOT_STRUCTURAL_BASE: u16 = 64;
pub const MULTIPLAYER_ACTION_SLOT_POS: u16 = MULTIPLAYER_ACTION_SLOT_STRUCTURAL_BASE + 188;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PacketHeader {
    pub packet_type: u8,
    pub client_id: u16,
}

#[derive(Debug, thiserror::Error)]
pub enum PacketError {
    #[error("packet is too short")]
    TooShort,
    #[error("invalid packet magic {0:#x}")]
    InvalidMagic(u32),
    #[error("unsupported protocol version {0}")]
    UnsupportedVersion(u8),
}

pub fn read_header(packet: &[u8]) -> Result<PacketHeader, PacketError> {
    if packet.len() < 8 {
        return Err(PacketError::TooShort);
    }
    let magic = u32::from_le_bytes(packet[0..4].try_into().expect("slice length checked"));
    if magic != MAGIC {
        return Err(PacketError::InvalidMagic(magic));
    }
    let version = packet[4];
    if version != VERSION {
        return Err(PacketError::UnsupportedVersion(version));
    }
    Ok(PacketHeader {
        packet_type: packet[5],
        client_id: u16::from_le_bytes(packet[6..8].try_into().expect("slice length checked")),
    })
}

pub fn write_header(packet_type: u8, client_id: u16, out: &mut Vec<u8>) {
    out.extend_from_slice(&MAGIC.to_le_bytes());
    out.push(VERSION);
    out.push(packet_type);
    out.extend_from_slice(&client_id.to_le_bytes());
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn header_round_trips_in_java_protocol_layout() {
        let mut bytes = Vec::new();
        write_header(HEARTBEAT, 42, &mut bytes);

        assert_eq!(
            read_header(&bytes).unwrap(),
            PacketHeader {
                packet_type: HEARTBEAT,
                client_id: 42
            }
        );
        assert_eq!(bytes, vec![0x53, 0x58, 0x4d, 0x50, 1, 4, 42, 0]);
    }
}
