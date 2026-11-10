package com.edu360.api.auth;

import java.lang.reflect.RecordComponent;
import java.util.Locale;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * PD-1 / I-05: a temporary password is shown once, on the response that created it, and must
 * never be kept by a browser or proxy cache. Any response whose body is a record with a non-null
 * component named "...temporaryPassword" or "...tempPassword" (CreateUserResponse, PasswordResetResponse,
 * TopUserResponse, TenantResponse.firstAdminTemporaryPassword, PlatformUserResponse.tempPassword) gets
 * Cache-Control: no-store and Pragma: no-cache, whichever controller produced it - so a new
 * endpoint that returns one cannot forget.
 */
@ControllerAdvice
public class TemporaryPasswordResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (carriesTemporaryPassword(body)) {
            response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
            response.getHeaders().set(HttpHeaders.PRAGMA, "no-cache");
        }
        return body;
    }

    static boolean carriesTemporaryPassword(Object body) {
        if (body == null || !body.getClass().isRecord()) {
            return false;
        }
        for (RecordComponent component : body.getClass().getRecordComponents()) {
            String name = component.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith("temporarypassword") || name.endsWith("temppassword")) {
                try {
                    if (component.getAccessor().invoke(body) != null) {
                        return true;
                    }
                } catch (ReflectiveOperationException ex) {
                    return true; // cannot tell: treat as carrying one
                }
            }
        }
        return false;
    }
}
