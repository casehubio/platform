package io.casehub.extraction;

public interface MessageMatcher {
    MessageMatch match(String input);
}
