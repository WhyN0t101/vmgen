package io.vmgen.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VMGenApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(VMGenApiApplication.class, args);
    }

}
