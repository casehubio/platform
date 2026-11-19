package test.plugins;

import io.casehub.yaml.plugin.api.*;
import java.util.Map;

@Plugin(value = "record-field-test", description = "Tests standalone record field schema generation")
public record RecordFieldTestPlugin(
    @Required String name,
    @Optional Address address
) {
    public record Address(String street, String city, int postCode) {}

    @Execute
    public Result run() {
        return Result.of(Map.of());
    }
}
