package com.sivalabs.ft.features.domain;


import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",  // Disable auto-migration
        "spring.jpa.hibernate.ddl-auto=none"  // Disable schema validation
})
public class V6__add_developer_to_feature__MigrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        // Clean database
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
    }

    @Test
    void shouldMigrateDeveloperDataCorrectly() {
        // Given - migrate up to V5 (before the migration we want to test)
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("5")  // Stop at V5
                .load();
        flyway.migrate();
        // clean up the database
        jdbcTemplate.execute("TRUNCATE TABLE features, developers, comments, releases, products RESTART IDENTITY CASCADE");

        // Verify pre-migration state - developer_id column doesn't exist yet
        Integer columnCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) 
            FROM information_schema.columns 
            WHERE table_name = 'features' 
            AND column_name = 'developer_id'
            """, Integer.class);
        assertThat(columnCount).isEqualTo(0);

        // Insert test data in "old" structure
        jdbcTemplate.update("""
            INSERT INTO products (id, code, prefix, name, description, image_url, created_by, created_at)
            VALUES (9001, 'TEST-PROD', 'TP', 'Test Product', 'Test Description', 'https://example.com/image.png', 'test', NOW())
            """);

        jdbcTemplate.update("""
            INSERT INTO features (id, code, title, description, status, product_id, assigned_to, created_by, created_at)
            VALUES (9001, 'TEST-001', 'Test Feature 1', 'Description 1', 'NEW', 9001, 'John Doe', 'test', NOW())
            """);

        jdbcTemplate.update("""
            INSERT INTO features (id, code, title, description, status, product_id, assigned_to, created_by, created_at)
            VALUES (9002, 'TEST-002', 'Test Feature 2', 'Description 2', 'IN_PROGRESS', 9001, 'Jane Smith', 'test', NOW())
            """);

        jdbcTemplate.update("""
            INSERT INTO features (id, code, title, description, status, product_id, assigned_to, created_by, created_at)
            VALUES (9003, 'TEST-003', 'Test Feature 3', 'Description 3', 'NEW', 9001, NULL, 'test', NOW())
            """);

        jdbcTemplate.update("""
            INSERT INTO features (id, code, title, description, status, product_id, assigned_to, created_by, created_at)
            VALUES (9004, 'TEST-004', 'Test Feature 4', 'Description 4', 'NEW', 9001, 'John Doe', 'test', NOW())
            """);

        // When - run V6 migration
        flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("6")
                .load();
        flyway.migrate();

        // 1. developer_id column was added
        columnCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) 
            FROM information_schema.columns 
            WHERE table_name = 'features' 
            AND column_name = 'developer_id'
            """, Integer.class);
        assertThat(columnCount).isEqualTo(1);

        // 2. Developers were created from assigned_to values
        Integer developerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM developers WHERE name IN ('John Doe', 'Jane Smith')",
                Integer.class
        );
        assertThat(developerCount).isEqualTo(2);

        // 3. Features were linked to developers correctly
        Long johnDoeId = jdbcTemplate.queryForObject(
                "SELECT id FROM developers WHERE name = 'John Doe'",
                Long.class
        );

        Long feature1DeveloperId = jdbcTemplate.queryForObject(
                "SELECT developer_id FROM features WHERE code = 'TEST-001'",
                Long.class
        );
        assertThat(feature1DeveloperId).isEqualTo(johnDoeId);

        Long feature3DeveloperId = jdbcTemplate.queryForObject(
                "SELECT developer_id FROM features WHERE code = 'TEST-004'",
                Long.class
        );
        assertThat(feature3DeveloperId).isEqualTo(johnDoeId);

        // 4. Different developer for feature 2
        Long janeSmithId = jdbcTemplate.queryForObject(
                "SELECT id FROM developers WHERE name = 'Jane Smith'",
                Long.class
        );

        Long feature2DeveloperId = jdbcTemplate.queryForObject(
                "SELECT developer_id FROM features WHERE code = 'TEST-002'",
                Long.class
        );
        assertThat(feature2DeveloperId).isEqualTo(janeSmithId);

        // 5. Feature without assigned_to should have NULL developer_id
        Long feature4DeveloperId = jdbcTemplate.queryForObject(
                "SELECT developer_id FROM features WHERE code = 'TEST-003'",
                Long.class
        );
        assertThat(feature4DeveloperId).isNull();

        // 6. Verify NO developer was created for null assigned_to
        Integer nullDeveloperCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM developers WHERE name IS NULL",
                Integer.class
        );
        assertThat(nullDeveloperCount).isEqualTo(0);

        // 7. Verify the total developer count (should be exactly 2, not 3)
        Integer totalDeveloperCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM developers",
                Integer.class
        );
        assertThat(totalDeveloperCount).isEqualTo(2);
    }
}
