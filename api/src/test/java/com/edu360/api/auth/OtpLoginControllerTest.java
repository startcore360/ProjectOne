package com.edu360.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.edu360.api.exception.ApiException;

class OtpLoginControllerTest {

    private final OtpLoginController controller = new OtpLoginController();

    @Test
    void status_reportsDisabledByDefault() {
        assertThat(controller.status().getBody()).isEqualTo(Map.of("enabled", false));
    }

    @Test
    void request_returnsProviderNotConfigured() {
        assertThatThrownBy(() -> controller.request(Map.of("mobile", "+919999999999")))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException api = (ApiException) error;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(api.getErrorCode()).isEqualTo("OTP_PROVIDER_NOT_CONFIGURED");
                });
    }

    @Test
    void verify_returnsProviderNotConfigured() {
        assertThatThrownBy(() -> controller.verify(Map.of("mobile", "+919999999999", "code", "123456")))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException api = (ApiException) error;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(api.getErrorCode()).isEqualTo("OTP_PROVIDER_NOT_CONFIGURED");
                });
    }
}
