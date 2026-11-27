# rxcontrols-icons-material

[Material Icons](https://fonts.google.com/icons?icon.set=Material+Icons) (filled style) for
`RXIcon`: 2122 icons with the name prefix `mi`.

```xml
<dependency>
    <groupId>io.github.leewyatt</groupId>
    <artifactId>rxcontrols-icons-material</artifactId>
    <version>1.0.0</version>
</dependency>
```

The pack registers itself through `ServiceLoader`, on the class path and on the module path.
`RXMaterialIcons` lists every name as a constant:

```java
RXIcon search = new RXIcon(RXMaterialIcons.SEARCH);   // "mi-search"
search.setIconSize(20);
```

```css
.toolbar .save-button .rx-icon {
    -rx-icon: "mi-save";
    -rx-icon-size: 1.5em;
}
```

A modular application reads `rxcontrols.icons.material` only to use the constants; the pack itself
is found by service binding.

## License

The icons are made by Google and licensed under the Apache License 2.0; the license text ships in
the jar as `META-INF/LICENSE-material-icons.txt`.

## Updating the icons

The data comes from the npm package
[`@material-design-icons/svg`](https://www.npmjs.com/package/@material-design-icons/svg)
(currently 0.14.15, the `filled` folder), which republishes Google's SVG files. Both generated
files, `material-icons.txt` and `RXMaterialIcons.java`, are written by `tools/icon-generator`;
do not edit them by hand. From the repository root:

```sh
curl -sLO https://registry.npmjs.org/@material-design-icons/svg/-/svg-0.14.15.tgz
mkdir material-svg && tar xzf svg-0.14.15.tgz -C material-svg
mvn -q install -pl rxcontrols -DskipTests
mvn -q -f tools/icon-generator/pom.xml compile exec:java -Dexec.args="\
  --svg material-svg/package/filled --prefix mi --title 'Material Icons' \
  --package io.github.leewyatt.rxcontrols.icons.material --class RXMaterialIcons \
  --java-out rxcontrols-icons-material/src/main/java \
  --resource-out rxcontrols-icons-material/src/main/resources/io/github/leewyatt/rxcontrols/icons/material/material-icons.txt \
  --source '@material-design-icons/svg 0.14.15, filled style (Material Icons by Google, Apache License 2.0)'"
```

Update the icon count in `RXMaterialIconsTest` and in this file when the set changes.
