package io.casehub.platform.api.process;

import java.util.List;

public class CommandNotAllowedException extends ProcessExecutionException {

    private final List<String> command;

    public CommandNotAllowedException(List<String> command) {
        super("Command not allowed: " + String.join(" ", command));
        this.command = List.copyOf(command);
    }

    public List<String> command() { return command; }
}
