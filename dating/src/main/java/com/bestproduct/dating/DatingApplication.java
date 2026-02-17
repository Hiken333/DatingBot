package com.bestproduct.dating;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Главный класс приложения Dating Bot
 * Telegram бот для знакомств
 * 
 * @author Dating Bot Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableConfigurationProperties
@EnableCaching
@EnableJpaRepositories
@EnableTransactionManagement
@EnableAsync(proxyTargetClass = true)
@EnableScheduling
public class DatingApplication {

	public static void main(String[] args) {
		SpringApplication.run(DatingApplication.class, args);
	}
}


