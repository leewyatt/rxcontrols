# rxcontrols-icons-bootstrap

[Bootstrap Icons](https://icons.getbootstrap.com/) for `RXIcon`: 2077 icons with the name prefix
`bi`.

```xml
<dependency>
    <groupId>io.github.leewyatt</groupId>
    <artifactId>rxcontrols-icons-bootstrap</artifactId>
    <version>1.0.0</version>
</dependency>
```

The pack registers itself through `ServiceLoader`, on the class path and on the module path.
`RXBootstrapIcons` lists every name as a constant:

```java
RXIcon alarm = new RXIcon(RXBootstrapIcons.ALARM);   // "bi-alarm"
```

```css
.status .rx-icon {
    -rx-icon: "bi-check-circle-fill";
}
```

## Differences from the SVG files

- `RXIcon` fills one path with one fill rule. 97 icons cannot be joined into one path as they
  are: they draw some parts with the non-zero rule and others with the even-odd rule, use a
  transform, or repeat a part (`cc-square`). The generator merges the parts of each into one
  outline and checks that it draws like the original.
- `opencollective` is left out: it is a two-tone logo, and `RXIcon` has one color.

## License

The icons are made by The Bootstrap Authors and licensed under the MIT License; the license text
ships in the jar as `META-INF/LICENSE-bootstrap-icons.txt`.

## Updating the icons

The data comes from the npm package [`bootstrap-icons`](https://www.npmjs.com/package/bootstrap-icons)
(currently 1.13.2, the `icons` folder). Both generated files, `bootstrap-icons.txt` and
`RXBootstrapIcons.java`, are written by `tools/icon-generator`; do not edit them by hand. From
the repository root:

```sh
curl -sLO https://registry.npmjs.org/bootstrap-icons/-/bootstrap-icons-1.13.2.tgz
mkdir bootstrap-svg && tar xzf bootstrap-icons-1.13.2.tgz -C bootstrap-svg
mvn -q install -pl rxcontrols -DskipTests
mvn -q -f tools/icon-generator/pom.xml compile exec:java -Dexec.args="\
  --svg bootstrap-svg/package/icons --prefix bi --title 'Bootstrap Icons' \
  --package io.github.leewyatt.rxcontrols.icons.bootstrap --class RXBootstrapIcons \
  --java-out rxcontrols-icons-bootstrap/src/main/java \
  --resource-out rxcontrols-icons-bootstrap/src/main/resources/io/github/leewyatt/rxcontrols/icons/bootstrap/bootstrap-icons.txt \
  --source 'bootstrap-icons 1.13.2 (Bootstrap Icons by The Bootstrap Authors, MIT License)' \
  --merge-outlines --skip-unsupported"
```

Update the icon count in `RXBootstrapIconsTest` and in this file when the set changes.
