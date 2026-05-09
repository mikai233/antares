# Luban Demo Config

This directory contains the default Excel source-of-truth for the project's demo game configs. The path used by Gradle
tasks is configured by `lubanDataDir` in the root `gradle.properties`, so real projects can point at a local or shared
config-table checkout without changing build scripts.

The flow is:

1. Edit the Excel files under the configured `lubanDataDir`.
2. Run `config/luban/generate.sh` / `config/luban/generate.ps1` or the Gradle tasks below.
3. Luban generates:
    - Java model/table code into `common/src/generated/luban/java`
    - binary table files into `common/build/generated/luban/resources/luban`
4. The project checks in the generated Java sources plus Kotlin Luban metadata/bridge sources. Asteria KSP generates
   table accessors during build. The generated `.bytes` files are local build artifacts used by tests and publication
   tooling, not runtime source files. Runtime nodes consume the packaged `game-config.zip` publication artifact from
   the config center and unpack it in memory.

`luban_server.conf` is the default config used by the generation scripts. It points at
`Datas/__tables_server_used.xlsx`, which is a server-used table whitelist. This is intended as a project-level
trimming layer for real deployments where the upstream table definitions are too broad or not consistently grouped.
It does not replace Luban's regular `c` / `s` grouping model; when the source tables are maintained cleanly, the
server-used table list can simply mirror the normal table list.

Common commands:

```bash
# Export Luban Java code and raw .bytes
./gradlew :common:exportLubanConfig

# Export Luban Java/.bytes and refresh the generated Kotlin metadata/bridge
./gradlew :common:refreshLubanConfig

# Build a server-consumable binary bundle
./gradlew :common:packageLubanConfigBundle
```

Publishing to the local config center defaults the publication revision to `projectVersion` from the root
`gradle.properties`:

```bash
./gradlew :tools:publishLocalGameConfig
```

Use `-PgameConfigVersion` only when intentionally publishing a config revision that differs from the code version:

```bash
./gradlew :tools:publishLocalGameConfig -PgameConfigVersion=4.3.0
```

That writes paths such as `/antares/game-config/revisions/4.3.0/...` while keeping the content checksum in the
publication manifest.

Default bundle output:

```text
common/build/generated/luban/bundles/game-config.zip
```

Common configuration in the root `gradle.properties`:

```properties
lubanDataDir=config/luban/Datas
# lubanToolRoot=/path/to/luban/tool/root
```

Temporary overrides are also supported:

```bash
./gradlew :common:refreshLubanConfig -PlubanDataDir=/path/to/Datas
./gradlew :common:refreshLubanConfig -PlubanToolRoot=/path/to/luban/tool/root
LUBAN_DATA_DIR=/path/to/Datas ./gradlew :common:refreshLubanConfig
LUBAN_TOOL_ROOT=/path/to/luban/tool/root ./gradlew :common:refreshLubanConfig
```

The generator expects an existing Luban examples checkout because it reuses the official tool and Java corelib:

- Gradle config: `lubanToolRoot=/path/to/luban/tool/root`
- direct script config: `LUBAN_TOOL_ROOT=/path/to/luban/tool/root`

If you don't have the repo locally:

```bash
git clone --depth 1 https://github.com/focus-creative-games/luban_examples /path/to/luban/tool/root
```

Then regenerate:

```bash
./config/luban/generate.sh
```

On Windows PowerShell:

```powershell
./config/luban/generate.ps1
```
