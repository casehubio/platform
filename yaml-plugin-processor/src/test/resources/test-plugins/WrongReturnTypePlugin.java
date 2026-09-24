package test.plugins;

import io.casehub.yaml.plugin.api.*;

@StepPlugin("wrong-return")
public record WrongReturnTypePlugin(@Required String name) {
    @Execute
    public void run() {
    }
}
