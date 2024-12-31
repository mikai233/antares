use ring::agreement::{EphemeralPrivateKey, PublicKey};

#[derive(Debug)]
pub struct KeyPair {
    pub private_key: Option<EphemeralPrivateKey>,
    pub public_key: PublicKey,
}

impl KeyPair {
    pub fn new(private_key: EphemeralPrivateKey, public_key: PublicKey) -> Self {
        KeyPair {
            private_key: Some(private_key),
            public_key,
        }
    }
}
