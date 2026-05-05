# Luban Demo Config

This directory contains the Excel source-of-truth for the project's demo game configs.

The flow is:

1. Edit the Excel files under `Datas/`.
2. Run `config/luban/generate.sh` / `config/luban/generate.ps1` or the Gradle tasks below.
3. Luban generates:
    - Java model/table code into `common/src/generated/luban/java`
    - binary table files into `common/build/generated/luban/resources/luban`
4. The project checks in the generated Java sources plus Kotlin Luban metadata/bridge sources. Asteria KSP generates
   table accessors during build. The generated `.bytes` files are local build artifacts used by tests and publication
   tooling, not runtime source files.

Common commands:

```bash
# Export Luban Java code and raw .bytes
./gradlew :common:exportLubanConfig

# Export Luban Java/.bytes and refresh the generated Kotlin metadata/bridge
./gradlew :common:refreshLubanConfig

# Build a server-consumable binary bundle
./gradlew :common:packageLubanConfigBundle
```

Default bundle output:

```text
common/build/generated/luban/bundles/game-config.zip
```

Optional parameters:

```bash
# Use a custom Excel directory
-PlubanDataDir=/path/to/Datas

# Use a custom bundle output directory (resolved from the repo root)
-PlubanBundleOutputDir=dist/config
```

The generator script expects an existing Luban examples checkout because it reuses the official tool and Java corelib:

- default location: `/tmp/luban_examples`
- override with: `LUBAN_EXAMPLES_ROOT=/path/to/luban_examples`

If you don't have the repo locally:

```bash
git clone --depth 1 https://github.com/focus-creative-games/luban_examples /tmp/luban_examples
```

Then regenerate:

```bash
./config/luban/generate.sh
```

On Windows PowerShell:

```powershell
./config/luban/generate.ps1
```
