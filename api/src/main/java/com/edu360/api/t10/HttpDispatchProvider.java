package com.edu360.api.t10;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Configurable HTTP adapter for PUSH, SMS and EMAIL gateways. The gateway URL and bearer
 * credential are deployment secrets; no vendor SDK or credential is bundled with EDU 360.
 * The receiver must honour X-Idempotency-Key to make worker retries safe.
 */
@Component
public class HttpDispatchProvider implements DispatchProvider {
    private final RestClient client;
    private final Map<String,Gateway> gateways=new LinkedHashMap<>();
    @org.springframework.beans.factory.annotation.Autowired
    public HttpDispatchProvider(
            @Value("${app.dispatch.providers.push.url:}") String pushUrl,
            @Value("${app.dispatch.providers.push.token:}") String pushToken,
            @Value("${app.dispatch.providers.sms.url:}") String smsUrl,
            @Value("${app.dispatch.providers.sms.token:}") String smsToken,
            @Value("${app.dispatch.providers.email.url:}") String emailUrl,
            @Value("${app.dispatch.providers.email.token:}") String emailToken,
            RestClient.Builder builder) {
        this(configuredClient(builder),pushUrl,pushToken,smsUrl,smsToken,emailUrl,emailToken);
    }
    HttpDispatchProvider(RestClient client,String pushUrl,String pushToken,String smsUrl,String smsToken,String emailUrl,String emailToken) {
        this.client=client;
        gateways.put("PUSH",new Gateway(pushUrl,pushToken));
        gateways.put("SMS",new Gateway(smsUrl,smsToken));
        gateways.put("EMAIL",new Gateway(emailUrl,emailToken));
    }
    private static RestClient configuredClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return builder.requestFactory(factory).build();
    }
    @Override public boolean supports(String channel) { return gateways.containsKey(channel); }
    @Override public DispatchProviderResult send(DispatchMessage message) {
        Gateway gateway=gateways.get(message.channel());
        if(gateway==null) throw new IllegalArgumentException("Unsupported external dispatch channel.");
        if(gateway.url().isBlank() || gateway.token().isBlank())
            throw new IllegalStateException(message.channel()+" provider is not configured; fallback will continue.");
        String destination=switch(message.channel()) {
            case "SMS" -> message.phone();
            case "EMAIL" -> message.email();
            default -> message.recipientUserId()==null?null:message.recipientUserId().toString();
        };
        if(destination==null || destination.isBlank())
            throw new IllegalArgumentException("Recipient destination is missing for "+message.channel()+".");
        Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("dispatchId",message.dispatchId().toString());
        payload.put("tenantId",message.tenantId().toString());
        payload.put("institutionId",message.institutionId().toString());
        payload.put("recipientUserId",message.recipientUserId()==null?null:message.recipientUserId().toString());
        payload.put("to",destination);
        payload.put("body",message.body());
        Map<?,?> response=client.post().uri(gateway.url())
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION,"Bearer "+gateway.token())
            .header("X-Idempotency-Key",message.dispatchId().toString())
            .body(payload).retrieve().body(Map.class);
        Object id=response==null?null:response.get("messageId");
        return DispatchProviderResult.accepted(id==null?message.dispatchId().toString():String.valueOf(id));
    }
    private record Gateway(String url,String token) {}
}
