package io.casehub.platform.rest.spring.generator;

import java.util.List;
import java.util.Optional;

public class SampleCore {
    public List<String> listItems(String tenancyId) { return List.of(); }
    public Optional<String> getItem(String id) { return Optional.empty(); }
    public String createItem(String body) { return ""; }
    public void deleteItem(String id) {}
}
