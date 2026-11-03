package io.casehub.yaml.test;

import java.util.List;

public final class TestReport {

    private TestReport() {}

    public static String toJunitXml(List<TestResult> results, String suiteName) {
        int tests = results.size();
        int failures = (int) results.stream().filter(r -> r.status() == TestStatus.FAIL).count();
        int errors = (int) results.stream().filter(r -> r.status() == TestStatus.ERROR).count();
        int skipped = (int) results.stream().filter(r -> r.status() == TestStatus.SKIP).count();
        long totalTime = results.stream().mapToLong(TestResult::durationMs).sum();

        var sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<testsuite name=\"").append(escapeXml(suiteName))
                .append("\" tests=\"").append(tests)
                .append("\" failures=\"").append(failures)
                .append("\" errors=\"").append(errors)
                .append("\" skipped=\"").append(skipped)
                .append("\" time=\"").append(totalTime / 1000.0)
                .append("\">\n");

        for (var result : results) {
            sb.append("  <testcase name=\"").append(escapeXml(result.name()))
                    .append("\" classname=\"").append(escapeXml(result.file().toString()))
                    .append("\" time=\"").append(result.durationMs() / 1000.0)
                    .append("\"");

            if (result.status() == TestStatus.PASS) {
                sb.append("/>\n");
            } else {
                sb.append(">\n");
                switch (result.status()) {
                    case FAIL -> {
                        for (var failure : result.failures()) {
                            sb.append("    <failure message=\"")
                                    .append(escapeXml(failure.message()))
                                    .append("\"");
                            if (failure.stepName() != null) {
                                sb.append(" type=\"step:").append(escapeXml(failure.stepName())).append("\"");
                            }
                            sb.append("/>\n");
                        }
                    }
                    case ERROR -> {
                        for (var failure : result.failures()) {
                            sb.append("    <error message=\"")
                                    .append(escapeXml(failure.message()))
                                    .append("\"/>\n");
                        }
                    }
                    case SKIP -> {
                        String reason = result.failures().isEmpty() ? "" : result.failures().get(0).message();
                        sb.append("    <skipped message=\"")
                                .append(escapeXml(reason))
                                .append("\"/>\n");
                    }
                    default -> {}
                }
                sb.append("  </testcase>\n");
            }
        }

        sb.append("</testsuite>\n");
        return sb.toString();
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
