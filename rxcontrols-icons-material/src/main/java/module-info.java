/**
 * Material Icons (filled) for {@code RXIcon}: the {@code mi} icon pack.
 */
module rxcontrols.icons.material {
    requires rxcontrols;

    exports io.github.leewyatt.rxcontrols.icons.material;

    provides io.github.leewyatt.rxcontrols.RXIconPack
            with io.github.leewyatt.rxcontrols.icons.material.RXMaterialIconPack;
}
