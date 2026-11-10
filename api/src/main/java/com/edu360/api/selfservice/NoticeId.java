package com.edu360.api.selfservice;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NoticeId implements Serializable {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "notice_id", nullable = false)
    private UUID noticeId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NoticeId that)) return false;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(noticeId, that.noticeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, noticeId);
    }
}
