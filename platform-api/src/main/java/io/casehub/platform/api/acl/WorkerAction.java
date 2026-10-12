package io.casehub.platform.api.acl;

public record WorkerAction(String name, AclAction aclAction, ConsentTier defaultTier) {

    public static final WorkerAction READ_CONTEXT = new WorkerAction("read-context", AclAction.READ);
    public static final WorkerAction SIGNAL_CASE = new WorkerAction("signal-case", AclAction.WRITE);

    public static final WorkerAction SEARCH = new WorkerAction("search", AclAction.READ, ConsentTier.AUTO);
    public static final WorkerAction ADD_TO_CART = new WorkerAction("add-to-cart", AclAction.WRITE, ConsentTier.AUTO);
    public static final WorkerAction BOOK_CANCELLABLE = new WorkerAction("book-cancellable", AclAction.WRITE, ConsentTier.CONFIRM);
    public static final WorkerAction PURCHASE = new WorkerAction("purchase", AclAction.WRITE, ConsentTier.APPROVE_WITH_DETAILS);
    public static final WorkerAction BOOK_NON_REFUNDABLE = new WorkerAction("book-non-refundable", AclAction.WRITE, ConsentTier.APPROVE_WITH_DETAILS);

    public WorkerAction(String name, AclAction aclAction) {
        this(name, aclAction, ConsentTier.AUTO);
    }

    public WorkerAction {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (aclAction == null) {
            throw new IllegalArgumentException("aclAction must not be null");
        }
        if (defaultTier == null) {
            throw new IllegalArgumentException("defaultTier must not be null");
        }
    }
}
