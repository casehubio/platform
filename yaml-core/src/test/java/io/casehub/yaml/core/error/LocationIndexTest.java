package io.casehub.yaml.core.error;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocationIndexTest {

    @Test
    void transferFromCopiesLocationToTargetMap() {
        var source = new LocationIndex();
        var sourceMap = new LinkedHashMap<String, Object>();
        var loc = new SourceLocation("module.yaml", 5, 3);
        source.put(sourceMap, loc);

        var target = new LocationIndex();
        var targetMap = new LinkedHashMap<String, Object>();
        target.transferFrom(source, sourceMap, targetMap);

        assertThat(target.locationOf(targetMap)).isEqualTo(loc);
    }

    @Test
    void transferFromWalksNestedMaps() {
        var source = new LocationIndex();
        var outerSource = new LinkedHashMap<String, Object>();
        var innerSource = new LinkedHashMap<String, Object>();
        outerSource.put("child", innerSource);
        source.put(outerSource, new SourceLocation("mod.yaml", 1, 1));
        source.put(innerSource, new SourceLocation("mod.yaml", 3, 5));

        var target = new LocationIndex();
        var outerTarget = new LinkedHashMap<String, Object>();
        var innerTarget = new LinkedHashMap<String, Object>();
        outerTarget.put("child", innerTarget);

        target.transferFrom(source, outerSource, outerTarget);

        assertThat(target.locationOf(outerTarget).line()).isEqualTo(1);
        assertThat(target.locationOf(innerTarget).line()).isEqualTo(3);
        assertThat(target.locationOf(innerTarget).column()).isEqualTo(5);
    }

    @Test
    void transferFromWalksLists() {
        var source = new LocationIndex();
        var sourceList = new ArrayList<Object>();
        var item0 = new LinkedHashMap<String, Object>();
        var item1 = new LinkedHashMap<String, Object>();
        sourceList.add(item0);
        sourceList.add(item1);
        source.put(sourceList, new SourceLocation("mod.yaml", 2, 1));
        source.put(item0, new SourceLocation("mod.yaml", 3, 5));
        source.put(item1, new SourceLocation("mod.yaml", 6, 5));

        var target = new LocationIndex();
        var targetList = new ArrayList<Object>();
        var targetItem0 = new LinkedHashMap<String, Object>();
        var targetItem1 = new LinkedHashMap<String, Object>();
        targetList.add(targetItem0);
        targetList.add(targetItem1);

        target.transferFrom(source, sourceList, targetList);

        assertThat(target.locationOf(targetList).line()).isEqualTo(2);
        assertThat(target.locationOf(targetItem0).line()).isEqualTo(3);
        assertThat(target.locationOf(targetItem1).line()).isEqualTo(6);
    }

    @Test
    void transferFromSkipsMissingKeys() {
        var source = new LocationIndex();
        var sourceMap = new LinkedHashMap<String, Object>();
        var sourceChild = new LinkedHashMap<String, Object>();
        sourceMap.put("a", sourceChild);
        source.put(sourceChild, new SourceLocation("mod.yaml", 2, 1));

        var target = new LocationIndex();
        var targetMap = new LinkedHashMap<String, Object>();

        target.transferFrom(source, sourceMap, targetMap);

        assertThat(target.locationOf(targetMap)).isEqualTo(SourceLocation.UNKNOWN);
    }

    @Test
    void transferFromHandlesScalarValues() {
        var source = new LocationIndex();
        var sourceMap = new LinkedHashMap<String, Object>();
        sourceMap.put("key", "scalar-value");
        var loc = new SourceLocation("mod.yaml", 1, 1);
        source.put(sourceMap, loc);

        var target = new LocationIndex();
        var targetMap = new LinkedHashMap<String, Object>();
        targetMap.put("key", "scalar-value");

        target.transferFrom(source, sourceMap, targetMap);

        assertThat(target.locationOf(targetMap)).isEqualTo(loc);
    }
}
