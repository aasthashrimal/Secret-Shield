package com.secretshield;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration.class)
public class SecretShieldApplication {

	public static void main(String[] args) {
		SpringApplication.run(SecretShieldApplication.class, args);
	}

}
