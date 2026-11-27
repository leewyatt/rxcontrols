/**
 * Bootstrap Icons for {@code RXIcon}: the {@code bi} icon pack.
 */
module rxcontrols.icons.bootstrap {
    requires rxcontrols;

    exports io.github.leewyatt.rxcontrols.icons.bootstrap;

    provides io.github.leewyatt.rxcontrols.RXIconPack
            with io.github.leewyatt.rxcontrols.icons.bootstrap.RXBootstrapIconPack;
}
