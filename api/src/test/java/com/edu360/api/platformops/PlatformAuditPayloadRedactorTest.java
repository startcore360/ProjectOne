package com.edu360.api.platformops;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class PlatformAuditPayloadRedactorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void redactsSensitiveKeysRecursivelyAndPreservesNonSensitiveValues() throws Exception {
        var source = mapper.readTree("""
                {
                  "old_values": {"password": "old", "state": "TRIAL"},
                  "new_values": [{"national_id": "1234", "salary": 5000, "status": "ACTIVE"}],
                  "token": "secret-token"
                }
                """);

        var redacted = PlatformAuditPayloadRedactor.redact(source);

        assertThat(redacted.at("/old_values/password").asText()).isEqualTo("***");
        assertThat(redacted.at("/old_values/state").asText()).isEqualTo("TRIAL");
        assertThat(redacted.at("/new_values/0/national_id").asText()).isEqualTo("***");
        assertThat(redacted.at("/new_values/0/salary").asText()).isEqualTo("***");
        assertThat(redacted.at("/new_values/0/status").asText()).isEqualTo("ACTIVE");
        assertThat(redacted.at("/token").asText()).isEqualTo("***");
    }
}
