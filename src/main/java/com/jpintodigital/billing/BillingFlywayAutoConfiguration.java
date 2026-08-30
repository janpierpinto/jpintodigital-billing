package com.jpintodigital.billing;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * A lib é dona do próprio schema (tabelas {@code billing_*}). As migrações em
 * {@code classpath:billing/migration} rodam num Flyway <b>dedicado</b>, com
 * tabela de histórico própria ({@code flyway_schema_history_billing}) — assim as
 * versões da lib nunca colidem nem ficam fora de ordem com as {@code V*} do host.
 *
 * <p>Usa as credenciais de migração do host ({@code spring.flyway.url/user/password})
 * quando presentes — que costumam ser um papel com DDL, distinto do papel de
 * runtime (RLS). Sem elas, cai no {@link DataSource} primário. Desligue com
 * {@code jp.billing.manage-schema=false} se o schema for gerenciado por fora.
 *
 * <p>O bean não é do tipo {@link Flyway} de propósito: um {@code @Bean Flyway}
 * dispararia o {@code @ConditionalOnMissingBean(Flyway.class)} do Boot e
 * suprimiria o Flyway do próprio host.
 */
@AutoConfiguration(
        afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        beforeName = "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration")
@ConditionalOnClass(Flyway.class)
@ConditionalOnProperty(prefix = "jp.billing", name = "manage-schema", matchIfMissing = true)
public class BillingFlywayAutoConfiguration {

    static final String LOCATION = "classpath:billing/migration";
    static final String HISTORY_TABLE = "flyway_schema_history_billing";

    @Bean
    BillingSchemaMigrator billingSchemaMigrator(DataSource dataSource, Environment environment) {
        return new BillingSchemaMigrator(dataSource, environment);
    }

    /**
     * Garante que a validação do Hibernate ({@code ddl-auto: validate}) só corre
     * depois que as tabelas {@code billing_*} existem.
     */
    @Bean
    static EntityManagerFactoryDependsOnPostProcessor billingSchemaMigratorEmfDependsOn() {
        return new EntityManagerFactoryDependsOnPostProcessor("billingSchemaMigrator");
    }

    /** Roda o Flyway dedicado da lib assim que o {@link DataSource} está pronto. */
    static class BillingSchemaMigrator implements InitializingBean {

        private final DataSource primaryDataSource;
        private final Environment environment;

        BillingSchemaMigrator(DataSource primaryDataSource, Environment environment) {
            this.primaryDataSource = primaryDataSource;
            this.environment = environment;
        }

        @Override
        public void afterPropertiesSet() {
            Flyway.configure(getClass().getClassLoader())
                    .dataSource(migrationDataSource())
                    .locations(LOCATION)
                    .table(HISTORY_TABLE)
                    .baselineOnMigrate(true)
                    .load()
                    .migrate();
        }

        private DataSource migrationDataSource() {
            var url = environment.getProperty("spring.flyway.url");
            if (url == null || url.isBlank()) {
                return primaryDataSource;
            }
            var ds = new DriverManagerDataSource(
                    url,
                    environment.getProperty("spring.flyway.user"),
                    environment.getProperty("spring.flyway.password"));
            var driver = environment.getProperty("spring.flyway.driver-class-name");
            if (driver != null && !driver.isBlank()) {
                ds.setDriverClassName(driver);
            }
            return ds;
        }
    }
}
