# Icon pack generator

A build-time tool that turns a folder of single-color SVG files into the two files of an
rxcontrols icon pack:

- a text resource with one icon per line, read by `RXIconPack.fromResource`;
- a class of name constants, such as `RXMaterialIcons`.

Each SVG file is read with `RXSvgIcons`, so the generator accepts exactly what the library
accepts: paths and basic shapes, also inside groups; elements with `fill="none"` are skipped.
Strokes, transforms, clipping, gradients, partial opacity and mixed fill rules stop the run,
unless `--skip-unsupported` is given, which lists the skipped files instead.

File names become icon names: lowercase, with underscores turned into hyphens
(`account_circle.svg` becomes `mi-account-circle`). Constants are the upper-case names with
underscores (`ACCOUNT_CIRCLE`), with a leading underscore when the name starts with a digit
(`_10K`).

| Option | Meaning |
|---|---|
| `--svg <dir>` | folder of `.svg` files (not searched recursively) |
| `--prefix <p>` | pack prefix: lowercase letters and digits |
| `--title <t>` | pack title |
| `--package <pkg>` / `--class <name>` | the constants class |
| `--java-out <dir>` | source root the constants class is written into |
| `--resource-out <file>` | the text resource to write |
| `--source <text>` | optional: where the icons come from and their license |
| `--skip-unsupported` | optional: skip files that cannot be converted |

The tool is not published. See `rxcontrols-icons-material/README.md` for a full command line.
