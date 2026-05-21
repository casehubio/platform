package io.casehub.platform.api.identity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class TenancyConstantsTest {

    @Test
    void defaultTenantId_is_a_valid_uuid() {
        assertTrue(TenancyConstants.DEFAULT_TENANT_ID.matches(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    void platformTenantId_is_not_blank() {
        assertNotNull(TenancyConstants.PLATFORM_TENANT_ID);
        assertFalse(TenancyConstants.PLATFORM_TENANT_ID.isBlank());
    }

    @Test
    void defaultTenantId_and_platformTenantId_are_distinct() {
        assertNotEquals(TenancyConstants.DEFAULT_TENANT_ID, TenancyConstants.PLATFORM_TENANT_ID);
    }

    @Test
    void constructor_is_private() throws Exception {
        final Constructor<TenancyConstants> ctor = TenancyConstants.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()));
    }
}
