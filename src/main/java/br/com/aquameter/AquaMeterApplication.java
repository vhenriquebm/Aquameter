package br.com.aquameter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AquaMeterApplication {

    public static void main(String[] args) {
        SpringApplication.run(AquaMeterApplication.class, args);
    }
}
