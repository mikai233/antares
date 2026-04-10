use std::fmt::{Display, Formatter};
use std::io;

use crate::crypto::{CryptoError, decrypt, decryptor, encrypt, encryptor};
use anyhow::anyhow;
use bytes::{BufMut, BytesMut};
use tokio_util::codec::{Decoder, Encoder};

pub struct ProtoCodec {
    pub send_index: u16,
    pub recv_index: u16,
    pub share_key: Option<[u8; 32]>,
}

impl ProtoCodec {
    pub fn new() -> Self {
        Self {
            send_index: 0,
            recv_index: 0,
            share_key: None,
        }
    }

    pub fn set_share_key(&mut self, share_key: [u8; 32]) {
        self.share_key = Some(share_key);
    }
}

#[derive(Debug, Clone)]
pub struct ProtobufPacket {
    pub id: i32,
    pub body: Vec<u8>,
}

impl ProtobufPacket {
    pub fn new(id: i32, body: Vec<u8>) -> Self {
        Self { id, body }
    }
}

impl Decoder for ProtoCodec {
    type Item = ProtobufPacket;
    type Error = ProtoCodecError;

    fn decode(&mut self, src: &mut BytesMut) -> Result<Option<Self::Item>, Self::Error> {
        let buf_len = src.len();
        if buf_len < 4 {
            return Ok(None);
        }
        let frame_len = peek_i32(src, 0)?;
        if frame_len < 4 {
            return Err(anyhow!("invalid frame length: {frame_len}").into());
        }
        if frame_len as usize > buf_len {
            src.reserve(frame_len as usize - buf_len);
            Ok(None)
        } else {
            let mut src = src.split_to(frame_len as usize);
            let _ = src.split_to(4);
            if let Some(share_key) = &mut self.share_key {
                let decryptor = decryptor(share_key);
                let decrypted = decrypt(decryptor, &src)?;
                src = BytesMut::from(decrypted.as_slice());
            }
            let server_index = peek_i32(&src, 0)?;
            if server_index != i32::from(self.recv_index) {
                return Err(anyhow!(
                    "server packet index:{server_index}!= expected recv packet index:{}",
                    self.recv_index
                )
                .into());
            }
            let id = peek_i32(&src, 4)?;
            let origin_len = peek_i32(&src, 8)?;
            //remove header
            let _ = src.split_to(12);
            let mut body = src.to_vec();

            let mut decompressed = vec![0u8; origin_len as usize];
            lz4_flex::decompress_into(&body, &mut decompressed).map_err(|e| anyhow!(e))?;
            body = decompressed;
            self.recv_index = self.recv_index.wrapping_add(1);
            Ok(Some(ProtobufPacket::new(id, body)))
        }
    }
}

impl Encoder<ProtobufPacket> for ProtoCodec {
    type Error = ProtoCodecError;

    fn encode(&mut self, packet: ProtobufPacket, dst: &mut BytesMut) -> Result<(), Self::Error> {
        let mut bytes = BytesMut::new();
        bytes.put_i32(self.send_index as i32);
        bytes.put_i32(packet.id);
        let origin_len = packet.body.len() as i32;
        bytes.put_i32(origin_len);
        bytes.put_slice(&lz4_flex::compress(&packet.body));
        let mut body = bytes.to_vec();
        if let Some(share_key) = &mut self.share_key {
            let encryptor = encryptor(share_key);
            //cannot use one encryptor more than once
            body = encrypt(encryptor, body.as_slice())?;
        }
        let package_len = 4 + body.len();
        dst.put_i32(i32::try_from(package_len)?);
        dst.put_slice(body.as_slice());
        self.send_index = self.send_index.wrapping_add(1);
        Ok(())
    }
}

#[derive(Debug)]
pub enum ProtoCodecError {
    Anyhow(anyhow::Error),
    Io(io::Error),
    TryFromInt(std::num::TryFromIntError),
    Crypto(CryptoError),
}

impl Display for ProtoCodecError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self {
            ProtoCodecError::Io(e) => {
                write!(f, "{}", e)
            }
            ProtoCodecError::Anyhow(e) => {
                write!(f, "{}", e)
            }
            ProtoCodecError::TryFromInt(e) => {
                write!(f, "{}", e)
            }
            ProtoCodecError::Crypto(e) => {
                write!(f, "{}", e)
            }
        }
    }
}

fn peek_i32(src: &BytesMut, offset: usize) -> anyhow::Result<i32> {
    let mut bytes = [0u8; 4];
    bytes.copy_from_slice(&src[offset..(offset + 4)]);
    let num = i32::from_be_bytes(bytes);
    Ok(num)
}

impl From<io::Error> for ProtoCodecError {
    fn from(e: io::Error) -> ProtoCodecError {
        ProtoCodecError::Io(e)
    }
}

impl From<anyhow::Error> for ProtoCodecError {
    fn from(value: anyhow::Error) -> Self {
        ProtoCodecError::Anyhow(value)
    }
}

impl From<std::num::TryFromIntError> for ProtoCodecError {
    fn from(value: std::num::TryFromIntError) -> Self {
        ProtoCodecError::TryFromInt(value)
    }
}

impl From<CryptoError> for ProtoCodecError {
    fn from(value: CryptoError) -> Self {
        ProtoCodecError::Crypto(value)
    }
}

impl std::error::Error for ProtoCodecError {}

#[cfg(test)]
mod tests {
    use super::{ProtoCodec, ProtobufPacket, peek_i32};
    use bytes::BytesMut;
    use tokio_util::codec::{Decoder, Encoder};

    #[test]
    fn decode_handles_back_to_back_frames_without_crypto() {
        let packet1 = ProtobufPacket::new(101, b"first-body".to_vec());
        let packet2 = ProtobufPacket::new(202, b"second-body".to_vec());

        let mut encoder = ProtoCodec::new();
        let mut combined = BytesMut::new();
        encoder.encode(packet1.clone(), &mut combined).unwrap();
        encoder.encode(packet2.clone(), &mut combined).unwrap();

        let mut decoder = ProtoCodec::new();
        let decoded1 = decoder.decode(&mut combined).unwrap().unwrap();
        let decoded2 = decoder.decode(&mut combined).unwrap().unwrap();

        assert_eq!(decoded1.id, packet1.id);
        assert_eq!(decoded1.body, packet1.body);
        assert_eq!(decoded2.id, packet2.id);
        assert_eq!(decoded2.body, packet2.body);
        assert!(combined.is_empty());
    }

    #[test]
    fn decode_handles_back_to_back_frames_with_crypto() {
        let key = *b"12345678901234567890123456789012";
        let packet1 = ProtobufPacket::new(101, b"first-body".to_vec());
        let packet2 = ProtobufPacket::new(202, b"second-body".to_vec());

        let mut encoder = ProtoCodec::new();
        encoder.set_share_key(key);
        let mut combined = BytesMut::new();
        encoder.encode(packet1.clone(), &mut combined).unwrap();
        encoder.encode(packet2.clone(), &mut combined).unwrap();

        let mut decoder = ProtoCodec::new();
        decoder.set_share_key(key);
        let decoded1 = decoder.decode(&mut combined).unwrap().unwrap();
        let decoded2 = decoder.decode(&mut combined).unwrap().unwrap();

        assert_eq!(decoded1.id, packet1.id);
        assert_eq!(decoded1.body, packet1.body);
        assert_eq!(decoded2.id, packet2.id);
        assert_eq!(decoded2.body, packet2.body);
        assert!(combined.is_empty());
    }

    #[test]
    fn decode_does_not_advance_outbound_packet_index() {
        let packet1 = ProtobufPacket::new(101, b"first-body".to_vec());
        let packet2 = ProtobufPacket::new(202, b"second-body".to_vec());
        let outbound_packet = ProtobufPacket::new(303, b"client-body".to_vec());

        let mut server_codec = ProtoCodec::new();
        let mut inbound_frames = BytesMut::new();
        server_codec.encode(packet1, &mut inbound_frames).unwrap();
        server_codec.encode(packet2, &mut inbound_frames).unwrap();

        let mut client_codec = ProtoCodec::new();
        client_codec.decode(&mut inbound_frames).unwrap().unwrap();
        client_codec.decode(&mut inbound_frames).unwrap().unwrap();

        let mut outbound_frame = BytesMut::new();
        client_codec.encode(outbound_packet, &mut outbound_frame).unwrap();

        assert_eq!(peek_i32(&outbound_frame, 4).unwrap(), 0);
    }
}
