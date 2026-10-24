package pl.bankzartow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.bankzartow.common.ApplicationInfo;
import pl.bankzartow.common.ApplicationInfoRepository;

/**
 * Confirms that Flyway runs its migrations on a fresh (empty) PostgreSQL provided by
 * Testcontainers, and that the resulting schema is readable through Spring Data JPA.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MigrationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationInfoRepository applicationInfoRepository;

    @Test
    void flywayAppliedInitialMigrationOnEmptyDatabase() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);

        assertThat(applied).isGreaterThanOrEqualTo(1);
    }

    @Test
    void initialMigrationCreatedTechnicalTableReadableViaJpa() {
        assertThat(applicationInfoRepository.findAll())
                .extracting(ApplicationInfo::getName)
                .contains("bank-zartow");
    }
}
