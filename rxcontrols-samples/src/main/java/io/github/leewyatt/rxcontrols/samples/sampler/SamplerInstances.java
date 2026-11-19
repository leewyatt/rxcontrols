package io.github.leewyatt.rxcontrols.samples.sampler;

import javafx.scene.Node;
import javafx.scene.Parent;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates and finds instances of showcased types for the parts of a page that are
 * generated rather than written: the default-instance playground and the CSS reference.
 */
final class SamplerInstances {

    private static final Logger LOG = Logger.getLogger(SamplerInstances.class.getName());

    private SamplerInstances() {
    }

    /**
     * Creates a node of the given type with its no-argument constructor.
     *
     * @return the node, or {@code null} when the type is not a node or cannot be created
     *         without arguments
     */
    static Node create(Class<?> type) {
        if (!Node.class.isAssignableFrom(type)) {
            return null;
        }
        try {
            return (Node) type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.log(Level.FINE, "No default instance of " + type.getName(), e);
            return null;
        }
    }

    /**
     * Finds the first node of exactly the given type under a root, depth first.
     *
     * @return the node, or {@code null} when there is none
     */
    static Node find(Node root, Class<?> type) {
        if (root.getClass() == type) {
            return root;
        }
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
