# Luban Demo Config

This directory contains the Excel source-of-truth for the project's demo game configs.

The flow is:

1. Edit the Excel files under `Datas/`.
2. Run `config/luban/generate.sh`.
3. Luban generates:
   - Java model/table code into `common/src/generated/luban/java`
   - binary table files into `common/src/generated/luban/resources/luban`
4. The project loads those generated `.bytes` files at runtime and during config publication.

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
