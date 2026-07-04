package io.casehub.platform.datasource.memory;

import io.casehub.platform.api.datasource.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlphaDataSourceTest {

    @Test
    void subscribe_allEvents_receivesEverything() {
        AlphaDataSource<Object> ds = new AlphaDataSource<>();
        List<Object> received = new ArrayList<>();
        ds.subscribe(received::add);

        ds.add("hello");
        ds.add(42);
        assertThat(received).containsExactly("hello", 42);
    }

    @Test
    void subscribe_withTypeFilter_onlyMatchingType() {
        AlphaDataSource<Object> ds = new AlphaDataSource<>();
        List<String> strings = new ArrayList<>();
        ds.subscribe(new ClassObjectType<>(String.class), (DataProcessor<String>) strings::add);

        ds.add("hello");
        ds.add(42);
        ds.add("world");
        assertThat(strings).containsExactly("hello", "world");
    }

    @Test
    void subscribe_withTypeAndFilter_appliesBoth() {
        AlphaDataSource<Object> ds = new AlphaDataSource<>();
        List<Integer> large = new ArrayList<>();
        ds.subscribe(new ClassObjectType<>(Integer.class), i -> i > 10, (DataProcessor<Integer>) large::add);

        ds.add(5);
        ds.add(20);
        ds.add("not a number");
        ds.add(15);
        assertThat(large).containsExactly(20, 15);
    }

    @Test
    void subscribe_classConvenience_works() {
        AlphaDataSource<Object> ds = new AlphaDataSource<>();
        List<String> strings = new ArrayList<>();
        ds.subscribe(String.class, s -> s.length() > 3, (DataProcessor<String>) strings::add);

        ds.add("hi");
        ds.add("hello");
        ds.add(42);
        assertThat(strings).containsExactly("hello");
    }

    @Test
    void typeNode_shared_acrossSubscriptions() {
        AlphaDataSource<Object> ds = new AlphaDataSource<>();
        List<String> list1 = new ArrayList<>();
        List<String> list2 = new ArrayList<>();
        ds.subscribe(new ClassObjectType<>(String.class), (DataProcessor<String>) list1::add);
        ds.subscribe(new ClassObjectType<>(String.class), (DataProcessor<String>) list2::add);

        ds.add("shared");
        assertThat(list1).containsExactly("shared");
        assertThat(list2).containsExactly("shared");
    }

    @Test
    void filterExpression_shared_sameTypeAndExpression() {
        AlphaDataSource<Object> ds = new AlphaDataSource<>();
        List<Integer> list1 = new ArrayList<>();
        List<Integer> list2 = new ArrayList<>();
        FilterExpression<Integer> expr1 = new FilterExpression<>("jq", ". > 10", i -> i > 10);
        FilterExpression<Integer> expr2 = new FilterExpression<>("jq", ". > 10", i -> i > 10);
        ds.subscribe(new ClassObjectType<>(Integer.class), expr1, (DataProcessor<Integer>) list1::add);
        ds.subscribe(new ClassObjectType<>(Integer.class), expr2, (DataProcessor<Integer>) list2::add);

        ds.add(20);
        assertThat(list1).containsExactly(20);
        assertThat(list2).containsExactly(20);
    }

    @Test
    void unsubscribe_stopsDelivery() {
        AlphaDataSource<Object> ds = new AlphaDataSource<>();
        List<Object> received = new ArrayList<>();
        SubscriptionHandle handle = ds.subscribe(received::add);

        ds.add("before");
        handle.unsubscribe();
        ds.add("after");
        assertThat(received).containsExactly("before");
        assertThat(handle.isActive()).isFalse();
    }

    @Test
    void subscriberException_doesNotBlockOthers() {
        AlphaDataSource<Object> ds = new AlphaDataSource<>();
        List<Object> good = new ArrayList<>();
        ds.subscribe(o -> { throw new RuntimeException("boom"); });
        ds.subscribe(good::add);

        ds.add("test");
        assertThat(good).containsExactly("test");
    }
}
