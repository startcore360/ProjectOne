package com.edu360.api.t10;

public interface DispatchProvider {
    boolean supports(String channel);
    DispatchProviderResult send(DispatchMessage message);
}
