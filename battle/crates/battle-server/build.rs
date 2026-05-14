use std::path::PathBuf;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let manifest_dir = PathBuf::from(std::env::var("CARGO_MANIFEST_DIR")?);
    let repo_root = manifest_dir
        .ancestors()
        .nth(3)
        .ok_or("failed to resolve repository root")?;
    let proto_dir = repo_root.join("client-proto/src/main/proto");
    let battle_proto = proto_dir.join("client/proto_battle.proto");

    prost_build::Config::new().compile_protos(&[battle_proto], &[proto_dir])?;
    Ok(())
}
