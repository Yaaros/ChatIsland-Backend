package io.g8.customai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
        "io.g8.customai.knowledge","io.g8.customai.user","io.g8.customai.common"})
public class CustomAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomAiApplication.class, args);
    }

}
