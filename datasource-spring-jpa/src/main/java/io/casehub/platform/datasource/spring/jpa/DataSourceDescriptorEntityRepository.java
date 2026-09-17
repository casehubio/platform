package io.casehub.platform.datasource.spring.jpa;

import io.casehub.platform.datasource.jpa.DataSourceDescriptorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DataSourceDescriptorEntityRepository
        extends JpaRepository<DataSourceDescriptorEntity, DataSourceDescriptorEntity.PK> {

    Optional<DataSourceDescriptorEntity> findByPathAndTenancyId(String path, String tenancyId);
}
