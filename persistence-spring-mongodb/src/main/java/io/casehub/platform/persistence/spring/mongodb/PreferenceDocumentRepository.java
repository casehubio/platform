package io.casehub.platform.persistence.spring.mongodb;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PreferenceDocumentRepository extends MongoRepository<SpringPreferenceDocument, String> {

    Optional<SpringPreferenceDocument> findByTenancyIdAndScopeAndNamespaceAndNameAndSubKey(
            String tenancyId, String scope, String namespace, String name, String subKey);

    List<SpringPreferenceDocument> findByTenancyIdAndScopeAndNamespace(
            String tenancyId, String scope, String namespace);

    List<SpringPreferenceDocument> findByTenancyIdAndScope(String tenancyId, String scope);

    List<SpringPreferenceDocument> findByTenancyIdAndNamespace(String tenancyId, String namespace);

    List<SpringPreferenceDocument> findByTenancyId(String tenancyId);

    List<SpringPreferenceDocument> findByTenancyIdAndScopeIn(String tenancyId, List<String> scopes);

    void deleteByTenancyIdAndScopeAndNamespaceAndNameAndSubKey(
            String tenancyId, String scope, String namespace, String name, String subKey);

    void deleteByTenancyIdAndScopeAndNamespace(String tenancyId, String scope, String namespace);
}
