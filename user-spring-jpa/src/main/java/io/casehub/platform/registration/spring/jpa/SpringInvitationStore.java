package io.casehub.platform.registration.spring.jpa;

import io.casehub.platform.registration.InvitationRecord;
import io.casehub.platform.registration.InvitationStore;
import io.casehub.platform.registration.jpa.InvitationEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public class SpringInvitationStore implements InvitationStore {

    private final InvitationEntityRepository repo;

    public SpringInvitationStore(InvitationEntityRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public InvitationRecord store(InvitationRecord invitation) {
        repo.save(InvitationEntity.fromRecord(invitation));
        return invitation;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InvitationRecord> findByTokenHash(String tokenHash) {
        return repo.findByTokenHash(tokenHash).map(InvitationEntity::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InvitationRecord> findById(String id) {
        return repo.findById(id).map(InvitationEntity::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InvitationRecord> findByEmail(String email, String tenancyId) {
        return repo.findFirstByEmailAndTenancyIdOrderByCreatedAtDesc(email, tenancyId)
                .map(InvitationEntity::toRecord);
    }

    @Override
    @Transactional
    public void consume(String tokenHash) {
        repo.findByTokenHash(tokenHash).ifPresent(e -> { e.consumed = true; repo.save(e); });
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvitationRecord> findByTenancy(String tenancyId) {
        return repo.findByTenancyIdOrderByCreatedAtDesc(tenancyId).stream()
                .map(InvitationEntity::toRecord).toList();
    }

    @Override
    @Transactional
    public void delete(String id) {
        repo.deleteById(id);
    }
}
