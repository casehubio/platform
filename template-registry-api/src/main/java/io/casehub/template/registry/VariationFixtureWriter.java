package io.casehub.template.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VariationFixtureWriter {

    private static final ObjectMapper YAML = new ObjectMapper(
        new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private VariationFixtureWriter() {}

    public static void write(Path file, String namespace, List<VariationFixture> fixtures) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("namespace", namespace);

        List<Map<String, Object>> fixtureList = new ArrayList<>();
        for (VariationFixture f : fixtures) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("template", f.templateName());
            entry.put("input", f.input());
            entry.put("expectMatch", f.expectMatch());
            if (!f.expectedFields().isEmpty()) {
                entry.put("expectedFields", new LinkedHashMap<>(f.expectedFields()));
            }
            fixtureList.add(entry);
        }
        doc.put("fixtures", fixtureList);

        try {
            Files.writeString(file, YAML.writeValueAsString(doc));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write fixture file: " + file, e);
        }
    }

    public static List<VariationFixture> fromReport(CorpusQualityReport report) {
        List<VariationFixture> fixtures = new ArrayList<>();
        for (TemplateReport template : report.templates()) {
            for (VariationResult result : template.variations()) {
                Map<String, String> fields = Map.of();
                if (result.expectedMatch()
                    && result.actualResult() instanceof TemplateMatch.Matched m) {
                    fields = m.fields();
                }
                fixtures.add(new VariationFixture(
                    template.templateName(),
                    result.input(),
                    result.expectedMatch(),
                    fields));
            }
        }
        return List.copyOf(fixtures);
    }
}
