package org.ergoplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ErgopayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ErgopayApplication.class, args);
	}

}
