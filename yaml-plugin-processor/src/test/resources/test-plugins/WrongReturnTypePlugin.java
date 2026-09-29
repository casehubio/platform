package test.plugins;

import io.casehub.yaml.plugin.api.*;

@Plugin("wrong-return")
public record WrongReturnTypePlugin(@Required String name) {
    @Execute
    public void run() {
    }
}
