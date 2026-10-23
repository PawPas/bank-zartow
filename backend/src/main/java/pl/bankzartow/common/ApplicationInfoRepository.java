package pl.bankzartow.common;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository over the technical {@link ApplicationInfo} table. Its only
 * purpose for now is to prove that Spring Data JPA is wired up against the migrated schema.
 */
public interface ApplicationInfoRepository extends JpaRepository<ApplicationInfo, Long> {
}
