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

    public static class Inner {
        public String value() { return ""; }
    }

    public static class Outer {
        public static class Nested {
            public static class Deep {}
        }
    }

    public Inner innerClass() { return new Inner(); }
    public Outer.Nested nestedInnerClass() { return new Outer.Nested(); }
    public Outer.Nested.Deep deeplyNestedInnerClass() { return new Outer.Nested.Deep(); }
    public List<Inner> listOfInnerClass() { return List.of(); }
    public Map<String, Inner> mapWithInnerClassValue() { return Map.of(); }
}
