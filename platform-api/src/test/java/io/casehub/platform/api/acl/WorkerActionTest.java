package io.casehub.platform.api.acl;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WorkerActionTest {

    @Test
    void constructsWithNameAndAclAction() {
        var action = new WorkerAction("READ_CONTEXT", AclAction.READ);
        assertEquals("READ_CONTEXT", action.name());
        assertEquals(AclAction.READ, action.aclAction());
    }

    @Test
    void equalityByValue() {
        assertEquals(
            new WorkerAction("READ_CONTEXT", AclAction.READ),
            new WorkerAction("READ_CONTEXT", AclAction.READ));
    }

    @Test
    void differentNameNotEqual() {
        assertNotEquals(
            new WorkerAction("READ_CONTEXT", AclAction.READ),
            new WorkerAction("WRITE_CONTEXT", AclAction.READ));
    }

    @Test
    void differentAclActionNotEqual() {
        assertNotEquals(
            new WorkerAction("READ_CONTEXT", AclAction.READ),
            new WorkerAction("READ_CONTEXT", AclAction.WRITE));
    }

    @Test
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class,
            () -> new WorkerAction("", AclAction.READ));
    }

    @Test
    void rejectsNullName() {
        assertThrows(IllegalArgumentException.class,
            () -> new WorkerAction(null, AclAction.READ));
    }

    @Test
    void rejectsNullAclAction() {
        assertThrows(IllegalArgumentException.class,
            () -> new WorkerAction("READ_CONTEXT", null));
    }

    @Test
    void twoArgConstructorDefaultsToAuto() {
        var action = new WorkerAction("test-action", AclAction.READ);
        assertEquals(ConsentTier.AUTO, action.defaultTier());
    }

    @Test
    void threeArgConstructorSetsDefaultTier() {
        var action = new WorkerAction("purchase", AclAction.WRITE, ConsentTier.APPROVE_WITH_DETAILS);
        assertEquals(ConsentTier.APPROVE_WITH_DETAILS, action.defaultTier());
    }

    @Test
    void rejectsNullDefaultTier() {
        assertThrows(IllegalArgumentException.class,
            () -> new WorkerAction("test", AclAction.READ, null));
    }

    @Test
    void searchConstantHasAutoTier() {
        assertEquals("search", WorkerAction.SEARCH.name());
        assertEquals(AclAction.READ, WorkerAction.SEARCH.aclAction());
        assertEquals(ConsentTier.AUTO, WorkerAction.SEARCH.defaultTier());
    }

    @Test
    void addToCartConstantHasAutoTier() {
        assertEquals("add-to-cart", WorkerAction.ADD_TO_CART.name());
        assertEquals(AclAction.WRITE, WorkerAction.ADD_TO_CART.aclAction());
        assertEquals(ConsentTier.AUTO, WorkerAction.ADD_TO_CART.defaultTier());
    }

    @Test
    void bookCancellableConstantHasConfirmTier() {
        assertEquals("book-cancellable", WorkerAction.BOOK_CANCELLABLE.name());
        assertEquals(AclAction.WRITE, WorkerAction.BOOK_CANCELLABLE.aclAction());
        assertEquals(ConsentTier.CONFIRM, WorkerAction.BOOK_CANCELLABLE.defaultTier());
    }

    @Test
    void purchaseConstantHasApproveWithDetailsTier() {
        assertEquals("purchase", WorkerAction.PURCHASE.name());
        assertEquals(AclAction.WRITE, WorkerAction.PURCHASE.aclAction());
        assertEquals(ConsentTier.APPROVE_WITH_DETAILS, WorkerAction.PURCHASE.defaultTier());
    }

    @Test
    void bookNonRefundableConstantHasApproveWithDetailsTier() {
        assertEquals("book-non-refundable", WorkerAction.BOOK_NON_REFUNDABLE.name());
        assertEquals(AclAction.WRITE, WorkerAction.BOOK_NON_REFUNDABLE.aclAction());
        assertEquals(ConsentTier.APPROVE_WITH_DETAILS, WorkerAction.BOOK_NON_REFUNDABLE.defaultTier());
    }

    @Test
    void existingConstantsDefaultToAuto() {
        assertEquals(ConsentTier.AUTO, WorkerAction.READ_CONTEXT.defaultTier());
        assertEquals(ConsentTier.AUTO, WorkerAction.SIGNAL_CASE.defaultTier());
    }
}
