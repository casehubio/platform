package io.casehub.platform.notification.settings.spring.jpa;

import io.casehub.platform.api.notification.settings.MuteRule;
import io.casehub.platform.api.notification.settings.MuteRuleInput;
import io.casehub.platform.api.notification.settings.Snooze;
import io.casehub.platform.api.notification.settings.SnoozeInput;
import io.casehub.platform.api.notification.settings.SuppressionStore;
import io.casehub.platform.notification.settings.jpa.MuteRuleEntity;
import io.casehub.platform.notification.settings.jpa.SnoozeEntity;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class SpringSuppressionStore implements SuppressionStore {

    private final MuteRuleEntityRepository muteRepo;
    private final SnoozeEntityRepository snoozeRepo;

    public SpringSuppressionStore(MuteRuleEntityRepository muteRepo, SnoozeEntityRepository snoozeRepo) {
        this.muteRepo = muteRepo;
        this.snoozeRepo = snoozeRepo;
    }

    @Override
    @Transactional
    public MuteRule addMute(MuteRuleInput input) {
        MuteRuleEntity entity = MuteRuleEntity.fromInput(input);
        muteRepo.saveAndFlush(entity);
        return entity.toMuteRule();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MuteRule> activeMutes(String userId, String tenancyId) {
        return muteRepo.findActiveMutes(userId, tenancyId, Instant.now()).stream()
                .map(MuteRuleEntity::toMuteRule)
                .toList();
    }

    @Override
    @Transactional
    public boolean removeMute(String muteId, String userId, String tenancyId) {
        return muteRepo.deleteByIdAndUserIdAndTenancyId(muteId, userId, tenancyId) > 0;
    }

    @Override
    @Transactional
    public Snooze activateSnooze(SnoozeInput input) {
        SnoozeEntity entity = SnoozeEntity.fromInput(input);
        SnoozeEntity saved = snoozeRepo.saveAndFlush(entity);
        return saved.toSnooze();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Snooze> activeSnooze(String userId, String tenancyId) {
        var pk = new SnoozeEntity.SnoozePK(userId, tenancyId);
        return snoozeRepo.findById(pk)
                .filter(e -> !Instant.now().isAfter(e.until))
                .map(SnoozeEntity::toSnooze);
    }

    @Override
    @Transactional
    public boolean cancelSnooze(String userId, String tenancyId) {
        return snoozeRepo.deleteByUserIdAndTenancyId(userId, tenancyId) > 0;
    }
}
