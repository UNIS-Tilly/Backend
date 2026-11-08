package com.unis.tilly.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// BaseEntity의 @CreatedDate/@LastModifiedDate가 동작하려면 Auditing이 켜져 있어야 한다.
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
