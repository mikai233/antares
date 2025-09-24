use crate::key_pair::KeyPair;
use ring::agreement::{agree_ephemeral, EphemeralPrivateKey, UnparsedPublicKey, X25519};
use ring::error::Unspecified;
use ring::rand::SystemRandom;

pub struct Ecdh;

impl Ecdh {
    pub fn calculate_shared_key(
        self_private_key: EphemeralPrivateKey,
        remote_public_key: &[u8],
    ) -> Result<Vec<u8>, Unspecified> {
        let peer_public_key = UnparsedPublicKey::new(&X25519, remote_public_key);
        let shared_key = agree_ephemeral(self_private_key, &peer_public_key, |key_material| {
            Ok::<Vec<u8>, Unspecified>(key_material.to_vec())
        })??;

        Ok(shared_key)
    }

    pub fn generate_key_pair() -> Result<KeyPair, Unspecified> {
        let rng = SystemRandom::new();
        let my_private_key = EphemeralPrivateKey::generate(&X25519, &rng)?;
        let my_public_key = my_private_key.compute_public_key()?;
        Ok(KeyPair::new(my_private_key, my_public_key))
    }
}
