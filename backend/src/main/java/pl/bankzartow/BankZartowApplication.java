package pl.bankzartow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import pl.bankzartow.auth.AuthProperties;

@SpringBootApplication
// Registered here (not on the security config) so the credentials are validated
// at startup even when security is inactive, e.g. non-web contexts. A blank or
// missing value fails startup via the @NotBlank constraints on AuthProperties.
@EnableConfigurationProperties(AuthProperties.class)
public class BankZartowApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankZartowApplication.class, args);
    }
}
