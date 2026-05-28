package pl.fireacademy;

import org.jspecify.annotations.NullMarked;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@NullMarked
@SpringBootApplication
public class FireAcademyApplication {

    public static void main(String[] args) {
        SpringApplication.run(FireAcademyApplication.class, args);
    }
}
