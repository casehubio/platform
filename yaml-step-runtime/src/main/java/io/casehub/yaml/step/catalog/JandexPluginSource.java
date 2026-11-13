package io.casehub.yaml.step.catalog;

import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.PluginRegistry;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JandexPluginSource {

    private static final Logger LOG = Logger.getLogger(JandexPluginSource.class.getName());
    private static final String PLUGIN_ANN = "io.casehub.yaml.plugin.api.Plugin";

    private final PluginScanner scanner = new PluginScanner();

    public void populate(PluginRegistry registry) {
        IndexView index = loadIndex();
        if (index == null) return;

        for (AnnotationInstance ann : index.getAnnotations(PLUGIN_ANN)) {
            if (ann.target().kind() != org.jboss.jandex.AnnotationTarget.Kind.CLASS) continue;
            String className = ann.target().asClass().name().toString();
            try {
                Class<?> pluginClass = Class.forName(className, true,
                        Thread.currentThread().getContextClassLoader());
                if (pluginClass.isAnnotationPresent(Plugin.class)) {
                    scanner.scanAndRegister(pluginClass, registry);
                }
            } catch (ClassNotFoundException e) {
                LOG.log(Level.FINE, "Skipping Jandex-discovered plugin (class not on classpath): " + className, e);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to register Jandex-discovered plugin: " + className, e);
            }
        }
    }

    private IndexView loadIndex() {
        List<IndexView> indexes = new ArrayList<>();
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = cl.getResources("META-INF/jandex.idx");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (InputStream is = url.openStream()) {
                    indexes.add(new IndexReader(is).read());
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to load Jandex indexes", e);
            return null;
        }
        if (indexes.isEmpty()) return null;
        return CompositeIndex.create(indexes);
    }
}
