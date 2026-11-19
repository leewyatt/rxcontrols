package io.github.leewyatt.rxcontrols.samples.sampler;

import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.application.Application;

import java.util.function.Supplier;

/**
 * One control shown on a sampler page. A page with several variants (a picker, its view
 * and their range forms) switches between them in place.
 *
 * @param label    the label of the variant switch
 * @param type     the showcased type
 * @param showcase creates the showcase hosted in the playground, or {@code null} to show a
 *                 default instance of {@code type}
 * @param demo     the minimal demo application, or {@code null} when there is none
 */
record SamplerVariant(String label, Class<?> type, Supplier<RXShowcaseApplication> showcase,
                      Class<? extends Application> demo) {

    String typeName() {
        return type.getSimpleName();
    }
}
