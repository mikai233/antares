use std::error::Error;
use std::fmt::{Display, Formatter};

use crypto::aes::ecb_decryptor;
use crypto::aes::KeySize::KeySize256;
use crypto::buffer::{self, BufferResult, ReadBuffer, WriteBuffer};
use crypto::symmetriccipher::{Decryptor, SymmetricCipherError};
use crypto::{aes::ecb_encryptor, blockmodes::PkcsPadding, symmetriccipher::Encryptor};

#[derive(Debug, Clone, Copy)]
pub enum CryptoError {
    InvalidLength,
    InvalidPadding,
}

pub fn encryptor(key: &[u8]) -> Box<dyn Encryptor> {
    ecb_encryptor(KeySize256, key, PkcsPadding)
}

pub fn decryptor(key: &[u8]) -> Box<dyn Decryptor> {
    ecb_decryptor(KeySize256, key, PkcsPadding)
}

pub fn encrypt(mut encryptor: Box<dyn Encryptor>, data: &[u8]) -> anyhow::Result<Vec<u8>> {
    let mut final_result = Vec::<u8>::new();
    let mut read_buffer = buffer::RefReadBuffer::new(data);
    let mut buffer = [0; 4096];
    let mut write_buffer = buffer::RefWriteBuffer::new(&mut buffer);
    loop {
        let result = encryptor
            .encrypt(&mut read_buffer, &mut write_buffer, true)
            .map_err(<SymmetricCipherError as Into<CryptoError>>::into)?;
        final_result.extend(
            write_buffer
                .take_read_buffer()
                .take_remaining()
                .iter()
                .copied(),
        );
        match result {
            BufferResult::BufferUnderflow => break,
            BufferResult::BufferOverflow => {}
        }
    }
    Ok(final_result)
}

pub fn decrypt(
    mut decryptor: Box<dyn Decryptor>,
    encrypted_data: &[u8],
) -> anyhow::Result<Vec<u8>> {
    let mut final_result = Vec::<u8>::new();
    let mut read_buffer = buffer::RefReadBuffer::new(encrypted_data);
    let mut buffer = [0; 4096];
    let mut write_buffer = buffer::RefWriteBuffer::new(&mut buffer);
    loop {
        let result = decryptor
            .decrypt(&mut read_buffer, &mut write_buffer, true)
            .map_err(<SymmetricCipherError as Into<CryptoError>>::into)?;
        final_result.extend(
            write_buffer
                .take_read_buffer()
                .take_remaining()
                .iter()
                .copied(),
        );
        match result {
            BufferResult::BufferUnderflow => break,
            BufferResult::BufferOverflow => {}
        }
    }
    Ok(final_result)
}

impl Display for CryptoError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self {
            CryptoError::InvalidLength => {
                write!(f, "InvalidLength")
            }
            CryptoError::InvalidPadding => {
                write!(f, "InvalidPadding")
            }
        }
    }
}

impl From<SymmetricCipherError> for CryptoError {
    fn from(value: SymmetricCipherError) -> Self {
        match value {
            SymmetricCipherError::InvalidLength => CryptoError::InvalidLength,
            SymmetricCipherError::InvalidPadding => CryptoError::InvalidPadding,
        }
    }
}

impl Error for CryptoError {}
