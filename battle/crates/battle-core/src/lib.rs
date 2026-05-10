#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum BattleFrameKind {
    Unspecified,
    Input,
    State,
    Ack,
    Error,
    Closed,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct BattleFrame {
    pub battle_id: i64,
    pub player_id: i64,
    pub sequence: i64,
    pub kind: BattleFrameKind,
    pub payload: Vec<u8>,
}

impl BattleFrame {
    pub fn input(battle_id: i64, player_id: i64, sequence: i64, payload: Vec<u8>) -> Self {
        Self {
            battle_id,
            player_id,
            sequence,
            kind: BattleFrameKind::Input,
            payload,
        }
    }
}

pub trait BattleSession {
    fn id(&self) -> i64;

    fn handle_frame(&mut self, frame: BattleFrame) -> Vec<BattleFrame>;
}
