package com.doubleledger.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "com.doubleledger")
@EntityScan(basePackages = "com.doubleledger")
@EnableJpaRepositories(basePackages = "com.doubleledger")
@EnableAsync
public class LedgerApplication {

	public static void main(String[] args) {
		SpringApplication.run(LedgerApplication.class, args);
	}

}
