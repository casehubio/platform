package io.casehub.platform.generator;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SampleTypes {
    public String simpleString() { return ""; }
    public List<String> parameterized() { return List.of(); }
    public Map<String, List<Integer>> nested() { return Map.of(); }
    public Optional<String> optional() { return Optional.empty(); }
    public void voidReturn() {}
    public int primitiveInt() { return 0; }
    public String[] arrayReturn() { return new String[0]; }
}
