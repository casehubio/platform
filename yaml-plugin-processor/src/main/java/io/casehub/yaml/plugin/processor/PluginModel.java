package io.casehub.yaml.plugin.processor;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import java.util.List;

record PluginModel(
        String name,
        String description,
        String portability,
        String capability,
        TypeElement pluginClass,
        List<RecordComponentElement> fields,
        ExecutableElement executeMethod,
        List<ServiceParam> serviceParams,
        boolean requiresConfirmation,
        String confirmationSummary,
        boolean streaming,
        String streamingInputType,
        String streamingOutputType,
        String streamingInputKey
) {
    record ServiceParam(String typeName, String qualifiedTypeName) {}
}
