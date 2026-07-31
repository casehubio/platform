package io.casehub.platform.api.acl;

public enum AclAction {
    READ,
    WRITE,
    ADMIN,
    CLAIM;

    private static final java.util.Set<AclAction> READ_SATISFIED_BY  = java.util.Set.of(READ, WRITE, ADMIN);
    private static final java.util.Set<AclAction> WRITE_SATISFIED_BY = java.util.Set.of(WRITE, ADMIN);
    private static final java.util.Set<AclAction> ADMIN_SATISFIED_BY = java.util.Set.of(ADMIN);
    private static final java.util.Set<AclAction> CLAIM_SATISFIED_BY = java.util.Set.of(CLAIM);

    public java.util.Set<AclAction> satisfiedBy() {
        return switch (this) {
            case READ -> READ_SATISFIED_BY;
            case WRITE -> WRITE_SATISFIED_BY;
            case ADMIN -> ADMIN_SATISFIED_BY;
            case CLAIM -> CLAIM_SATISFIED_BY;
        };
    }

    private static final java.util.Set<AclAction> READ_DENIED_BY  = java.util.Set.of(READ);
    private static final java.util.Set<AclAction> WRITE_DENIED_BY = java.util.Set.of(READ, WRITE);
    private static final java.util.Set<AclAction> ADMIN_DENIED_BY = java.util.Set.of(READ, WRITE, ADMIN);
    private static final java.util.Set<AclAction> CLAIM_DENIED_BY = java.util.Set.of(CLAIM);

    public java.util.Set<AclAction> deniedBy() {
        return switch (this) {
            case READ -> READ_DENIED_BY;
            case WRITE -> WRITE_DENIED_BY;
            case ADMIN -> ADMIN_DENIED_BY;
            case CLAIM -> CLAIM_DENIED_BY;
        };
    }

}
