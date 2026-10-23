package pl.bankzartow.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Minimal technical entity mapped onto the {@code application_info} table created by
 * {@code V1__init.sql}. It exists so that {@code ddl-auto=validate} has something to
 * check: if the migrated schema and this mapping drift apart, the application refuses
 * to start.
 */
@Entity
@Table(name = "application_info")
public class ApplicationInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    protected ApplicationInfo() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
