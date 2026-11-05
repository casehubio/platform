package io.casehub.yaml.test;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YamlTestCliTest {

    @Test
    void parseArgsDryRunFlag() {
        var args = YamlTestCli.parseArgs(new String[]{
                "--dry-run", "--corpus", "corpus.yaml",
                "--var", "key=val", "playbooks/"});
        assertThat(args.dryRun()).isTrue();
        assertThat(args.corpusPath()).isEqualTo("corpus.yaml");
        assertThat(args.vars()).containsEntry("key", "val");
        assertThat(args.paths()).containsExactly("playbooks/");
    }

    @Test
    void parseArgsDefaultsNotDryRun() {
        var args = YamlTestCli.parseArgs(new String[]{"tests/"});
        assertThat(args.dryRun()).isFalse();
        assertThat(args.corpusPath()).isNull();
        assertThat(args.vars()).isEmpty();
    }

    @Test
    void parseArgsProfileFlag() {
        var args = YamlTestCli.parseArgs(new String[]{
                "--profile", "dev", "--dry-run", "playbooks/"});
        assertThat(args.profile()).isEqualTo("dev");
        assertThat(args.dryRun()).isTrue();
    }

    @Test
    void parseArgsMultipleVars() {
        var args = YamlTestCli.parseArgs(new String[]{
                "--var", "a=1", "--var", "b=2", "playbooks/"});
        assertThat(args.vars()).containsEntry("a", "1");
        assertThat(args.vars()).containsEntry("b", "2");
    }

    @Test
    void isPlaybookFileDetectsYamlNotTest() {
        assertThat(YamlTestCli.isPlaybookFile("example.yaml")).isTrue();
        assertThat(YamlTestCli.isPlaybookFile("test.test.yaml")).isFalse();
        assertThat(YamlTestCli.isPlaybookFile("dir/playbook.yml")).isTrue();
        assertThat(YamlTestCli.isPlaybookFile("script.sh")).isFalse();
    }

    @Test
    void parseArgsFilterAndReport() {
        var args = YamlTestCli.parseArgs(new String[]{
                "--filter", "basic", "--report", "junit-xml",
                "--output", "/tmp/out", "tests/"});
        assertThat(args.filter()).isEqualTo("basic");
        assertThat(args.reportFormat()).isEqualTo("junit-xml");
        assertThat(args.outputDir()).isEqualTo("/tmp/out");
    }
}
