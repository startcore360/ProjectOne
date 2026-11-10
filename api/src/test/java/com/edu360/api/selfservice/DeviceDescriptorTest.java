package com.edu360.api.selfservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class DeviceDescriptorTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36 | COMPUTER | Chrome on Windows",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36 Edg/130.0 | COMPUTER | Edge on Windows",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15 | COMPUTER | Safari on macOS",
            "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0 | COMPUTER | Firefox on Linux",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1 | PHONE | Safari on iPhone",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36 | PHONE | Chrome on Android",
            "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/25.0 Chrome/121.0 Mobile Safari/537.36 | PHONE | Samsung Internet on Android",
    })
    void describesCommonBrowsers(String userAgent, String deviceClass, String label) {
        assertThat(DeviceDescriptor.deviceClass(userAgent)).isEqualTo(deviceClass);
        assertThat(DeviceDescriptor.label(userAgent)).isEqualTo(label);
    }

    @Test
    void unknownUserAgent_isAComputerWithNoLabel() {
        assertThat(DeviceDescriptor.deviceClass(null)).isEqualTo("COMPUTER");
        assertThat(DeviceDescriptor.label(null)).isNull();
        assertThat(DeviceDescriptor.label("curl/8.4.0")).isNull();
    }

    @Test
    void browserKey_isHashedAndMalformedKeysAreIgnored() {
        String hash = DeviceDescriptor.hashBrowserKey("0b6f3c1e-1d2a-4c3b-9e8f-7a6b5c4d3e2f");
        assertThat(hash).matches("^[0-9a-f]{64}$");
        assertThat(DeviceDescriptor.hashBrowserKey("0b6f3c1e-1d2a-4c3b-9e8f-7a6b5c4d3e2f")).isEqualTo(hash);
        assertThat(DeviceDescriptor.hashBrowserKey(null)).isNull();
        assertThat(DeviceDescriptor.hashBrowserKey("short")).isNull();
        assertThat(DeviceDescriptor.hashBrowserKey("has spaces and ; semicolons here")).isNull();
    }
}
