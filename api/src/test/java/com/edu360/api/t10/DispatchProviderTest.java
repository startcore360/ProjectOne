package com.edu360.api.t10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DispatchProviderTest {
    @Test
    void httpProviderUsesIdempotencyKeyAndReturnsGatewayMessageId() {
        RestClient.Builder builder=RestClient.builder();
        MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        RestClient client=builder.build();
        HttpDispatchProvider provider=new HttpDispatchProvider(
                client,"", "", "", "", "https://mail-gateway.test/send", "test-token");
        UUID dispatch=UUID.randomUUID();
        server.expect(requestTo("https://mail-gateway.test/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization","Bearer test-token"))
                .andExpect(header("X-Idempotency-Key",dispatch.toString()))
                .andRespond(withSuccess("{\"messageId\":\"gateway-42\"}",MediaType.APPLICATION_JSON));
        DispatchProviderResult result=provider.send(new DispatchMessage(dispatch,UUID.randomUUID(),
                UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                "EMAIL",null,"recipient@example.test","A message"));
        assertThat(result.providerMessageId()).isEqualTo("gateway-42");
        server.verify();
    }

    @Test
    void inAppProviderWritesDeterministicNotificationId() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        InAppDispatchProvider provider=new InAppDispatchProvider(jdbc);
        UUID dispatch=UUID.randomUUID();
        DispatchMessage message=new DispatchMessage(dispatch,UUID.randomUUID(),UUID.randomUUID(),
                UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"IN_APP",null,null,"Hello");
        DispatchProviderResult first=provider.send(message);
        DispatchProviderResult second=provider.send(message);
        assertThat(first.providerMessageId()).isEqualTo(second.providerMessageId());
        verify(jdbc,org.mockito.Mockito.times(2)).update(any(String.class),any(Object[].class));
    }
    @Test void missingGatewayConfigurationFailsClosed() {
        HttpDispatchProvider provider=new HttpDispatchProvider(RestClient.builder().build(),"","","","","","");
        DispatchMessage message=new DispatchMessage(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"SMS","+910000000000",null,"message");
        assertThatThrownBy(() -> provider.send(message)).isInstanceOf(IllegalStateException.class).hasMessageContaining("not configured");
    }

    @Test void missingGatewayConfigurationFailsClosed() {
        HttpDispatchProvider provider=new HttpDispatchProvider(RestClient.builder().build(),"","","","","","");
        DispatchMessage message=new DispatchMessage(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"SMS","+910000000000",null,"message");
        assertThatThrownBy(() -> provider.send(message)).isInstanceOf(IllegalStateException.class).hasMessageContaining("not configured");
    }

}
