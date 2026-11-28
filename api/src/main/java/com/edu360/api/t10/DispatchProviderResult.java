package com.edu360.api.t10;

public record DispatchProviderResult(String providerMessageId) {
    public static DispatchProviderResult accepted(String id) { return new DispatchProviderResult(id); }
}
