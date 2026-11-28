package com.edu360.api.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the shared Jackson mapper when the web starter does not auto-configure one.
 * The conditional keeps this compatible with deployments that provide their own mapper.
 */
@Configuration(proxyBeanMethods = false)
public class FinanceJacksonConfiguration {
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper financeObjectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }
}
