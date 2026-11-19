package io.casehub.platform.authn.social.jpa;

import io.casehub.platform.api.util.UUIDv7;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;

public class V3_1__BackfillCredentialHashes extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (var select = context.getConnection().prepareStatement(
                "SELECT actor_id, provider, tenancy_id, credential FROM authn_static_credential WHERE id IS NULL");
             var update = context.getConnection().prepareStatement(
                "UPDATE authn_static_credential SET id = ?, credential_hash = ? " +
                "WHERE actor_id = ? AND provider = ? AND tenancy_id = ? AND id IS NULL")) {

            try (ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                String actorId = rs.getString("actor_id");
                String provider = rs.getString("provider");
                String tenancyId = rs.getString("tenancy_id");
                String credential = rs.getString("credential");

                update.setString(1, UUIDv7.generate());
                update.setString(2, CredentialHasher.sha256Hex(credential));
                update.setString(3, actorId);
                update.setString(4, provider);
                update.setString(5, tenancyId);
                update.addBatch();
            }
            }
            update.executeBatch();
        }
    }
}
