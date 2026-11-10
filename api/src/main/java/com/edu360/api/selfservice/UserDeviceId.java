package com.edu360.api.selfservice;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDeviceId implements Serializable {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_device_id", nullable = false)
    private UUID userDeviceId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserDeviceId that)) return false;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(userDeviceId, that.userDeviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, userDeviceId);
    }
}
