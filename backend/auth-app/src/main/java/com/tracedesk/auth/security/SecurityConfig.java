package com.tracedesk.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new Argon2PasswordEncoder(16, 32, 1, 19456, 2); }
    @Bean FilterRegistrationBean<ResponsePolicyFilter> responsePolicyFilter() { FilterRegistrationBean<ResponsePolicyFilter> bean=new FilterRegistrationBean<>(); bean.setFilter(new ResponsePolicyFilter()); bean.setOrder(-200); return bean; }
    @Bean FilterRegistrationBean<PersistenceFailureFilter> persistenceFailureFilter() { FilterRegistrationBean<PersistenceFailureFilter> bean=new FilterRegistrationBean<>(); bean.setFilter(new PersistenceFailureFilter()); bean.setOrder(Integer.MIN_VALUE); return bean; }
    @Bean FilterRegistrationBean<LoginBodyLimitFilter> loginBodyLimitFilter(com.tracedesk.auth.persistence.AuthStore store) { FilterRegistrationBean<LoginBodyLimitFilter> bean=new FilterRegistrationBean<>(); bean.setFilter(new LoginBodyLimitFilter(store)); bean.setOrder(-100); return bean; }
    @Bean FilterRegistrationBean<ReportBodyLimitFilter> reportBodyLimitFilter(com.tracedesk.auth.persistence.AuthStore store) { FilterRegistrationBean<ReportBodyLimitFilter> bean=new FilterRegistrationBean<>(); bean.setFilter(new ReportBodyLimitFilter(store)); bean.setOrder(-100); return bean; }
    @Bean FilterRegistrationBean<InvestigationBodyLimitFilter> investigationBodyLimitFilter(com.tracedesk.auth.persistence.AuthStore store) { FilterRegistrationBean<InvestigationBodyLimitFilter> bean=new FilterRegistrationBean<>(); bean.setFilter(new InvestigationBodyLimitFilter(store)); bean.setOrder(-100); return bean; }
    @Bean FilterRegistrationBean<ProjectJoinBodyProbeFilter> projectJoinBodyProbeFilter() { FilterRegistrationBean<ProjectJoinBodyProbeFilter> bean=new FilterRegistrationBean<>(); bean.setFilter(new ProjectJoinBodyProbeFilter()); bean.setOrder(-90); return bean; }
    @Bean SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .headers(h -> h.contentTypeOptions(c -> {}).referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; frame-ancestors 'none'; base-uri 'self'"))
                .httpStrictTransportSecurity(s -> s.includeSubDomains(true).maxAgeInSeconds(31536000)));
        return http.build();
    }
}
