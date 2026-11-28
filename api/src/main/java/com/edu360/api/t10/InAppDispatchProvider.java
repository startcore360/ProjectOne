package com.edu360.api.t10;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Delivers the first fallback channel into the existing tenant notification inbox. */
@Component
public class InAppDispatchProvider implements DispatchProvider {
    private final JdbcTemplate jdbc;
    public InAppDispatchProvider(@Qualifier("jdbcTemplate") JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public boolean supports(String channel) { return "IN_APP".equals(channel); }
    @Override public DispatchProviderResult send(DispatchMessage message) {
        if(message.recipientUserId()==null) throw new IllegalArgumentException("In-app delivery requires a recipient user.");
        UUID notificationId=UUID.nameUUIDFromBytes(("edu360-dispatch:"+message.dispatchId()).getBytes(StandardCharsets.UTF_8));
        jdbc.update("""
            INSERT INTO app.notification
              (tenant_id,notification_id,recipient_user_id,institution_id,trigger_code,title,body,
               entity_type,entity_id,created_by_user_id,priority_class,dedupe_key,created_by,updated_by)
            VALUES (?,?,?,?,'COMMUNICATION_DISPATCH','New EDU 360 message',?,
                    'COMMUNICATION',?,?, 'NORMAL',?,?,?)
            ON CONFLICT (tenant_id,notification_id) DO NOTHING
            """,message.tenantId(),notificationId,message.recipientUserId(),message.institutionId(),
                message.body(),message.campaignId(),message.createdBy(),message.dispatchId().toString(),
                message.createdBy(),message.createdBy());
        return DispatchProviderResult.accepted(notificationId.toString());
    }
}
