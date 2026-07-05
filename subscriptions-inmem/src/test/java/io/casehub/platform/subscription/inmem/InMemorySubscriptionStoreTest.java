package io.casehub.platform.subscription.inmem;

import io.casehub.platform.api.notification.UUIDv7;
import io.casehub.platform.api.subscription.SubscriptionStore;
import io.casehub.platform.api.subscription.SubscriptionStoreContractTest;

class InMemorySubscriptionStoreTest extends SubscriptionStoreContractTest {

    private InMemorySubscriptionStore store;

    @Override
    protected SubscriptionStore store() {
        return store;
    }

    @Override
    protected void clearState() {
        store = new InMemorySubscriptionStore();
        UUIDv7.resetState(); // Reset thread-local UUID sequence state
    }
}
