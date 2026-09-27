package io.casehub.platform.api.process;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommandPatternTest {

    @Test
    void exactMatchSucceeds() {
        var pattern = new CommandPattern(List.of("python3", "/opt/scripts/deploy.py"),
                CommandPattern.MatchMode.EXACT);
        assertThat(pattern.matches(List.of("python3", "/opt/scripts/deploy.py"))).isTrue();
    }

    @Test
    void exactMatchRejectsDifferentArgs() {
        var pattern = new CommandPattern(List.of("python3", "/opt/scripts/deploy.py"),
                CommandPattern.MatchMode.EXACT);
        assertThat(pattern.matches(List.of("python3", "/opt/scripts/hack.py"))).isFalse();
    }

    @Test
    void exactMatchRejectsDifferentLength() {
        var pattern = new CommandPattern(List.of("python3"),
                CommandPattern.MatchMode.EXACT);
        assertThat(pattern.matches(List.of("python3", "script.py"))).isFalse();
    }

    @Test
    void prefixMatchSucceeds() {
        var pattern = new CommandPattern(List.of("python3", "/opt/scripts/"),
                CommandPattern.MatchMode.PREFIX);
        assertThat(pattern.matches(List.of("python3", "/opt/scripts/deploy.py"))).isTrue();
        assertThat(pattern.matches(List.of("python3", "/opt/scripts/sub/deep.py"))).isTrue();
    }

    @Test
    void prefixMatchRejectsWrongPrefix() {
        var pattern = new CommandPattern(List.of("python3", "/opt/scripts/"),
                CommandPattern.MatchMode.PREFIX);
        assertThat(pattern.matches(List.of("python3", "/tmp/hack.py"))).isFalse();
    }

    @Test
    void prefixMatchRequiresCommandMatch() {
        var pattern = new CommandPattern(List.of("python3", "/opt/scripts/"),
                CommandPattern.MatchMode.PREFIX);
        assertThat(pattern.matches(List.of("ruby", "/opt/scripts/deploy.rb"))).isFalse();
    }

    @Test
    void globMatchSuffix() {
        var pattern = new CommandPattern(List.of("python3", "/opt/scripts/*.py"),
                CommandPattern.MatchMode.GLOB);
        assertThat(pattern.matches(List.of("python3", "/opt/scripts/deploy.py"))).isTrue();
        assertThat(pattern.matches(List.of("python3", "/opt/scripts/deploy.sh"))).isFalse();
    }

    @Test
    void globMatchWildcard() {
        var pattern = new CommandPattern(List.of("sh", "-c", "*"),
                CommandPattern.MatchMode.GLOB);
        assertThat(pattern.matches(List.of("sh", "-c", "echo hello"))).isTrue();
    }

    @Test
    void emptyCommandNeverMatches() {
        var pattern = new CommandPattern(List.of("python3"),
                CommandPattern.MatchMode.EXACT);
        assertThat(pattern.matches(List.of())).isFalse();
    }

    @Test
    void commandNotAllowedExceptionCarriesCommand() {
        var ex = new CommandNotAllowedException(List.of("rm", "-rf", "/"));
        assertThat(ex.command()).containsExactly("rm", "-rf", "/");
        assertThat(ex.getMessage()).contains("rm -rf /");
        assertThat(ex).isInstanceOf(ProcessExecutionException.class);
    }
}
