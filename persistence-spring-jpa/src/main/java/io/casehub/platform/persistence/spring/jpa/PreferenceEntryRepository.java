package io.casehub.platform.persistence.spring.jpa;

import io.casehub.platform.persistence.jpa.PreferenceEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PreferenceEntryRepository extends JpaRepository<PreferenceEntry, Long> {

    Optional<PreferenceEntry> findByTenancyIdAndScopeAndNamespaceAndNameAndSubKey(
            String tenancyId, String scope, String namespace, String name, String subKey);

    List<PreferenceEntry> findByTenancyIdAndScopeAndNamespace(
            String tenancyId, String scope, String namespace);

    List<PreferenceEntry> findByTenancyIdAndScope(String tenancyId, String scope);

    List<PreferenceEntry> findByTenancyIdAndNamespace(String tenancyId, String namespace);

    List<PreferenceEntry> findByTenancyId(String tenancyId);

    List<PreferenceEntry> findByTenancyIdAndScopeIn(String tenancyId, List<String> scopes);

    @Modifying
    @Query("DELETE FROM PreferenceEntry e WHERE e.tenancyId = ?1 AND e.scope = ?2 AND e.namespace = ?3 AND e.name = ?4 AND e.subKey = ?5")
    int deleteByKey(String tenancyId, String scope, String namespace, String name, String subKey);

    @Modifying
    @Query("DELETE FROM PreferenceEntry e WHERE e.tenancyId = ?1 AND e.scope = ?2 AND e.namespace = ?3")
    int deleteByNamespace(String tenancyId, String scope, String namespace);
}
