package com.jpintodigital.billing;

import java.util.ArrayList;
import java.util.Collections;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
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
 * <p>Roda <b>depois</b> do Flyway do host (quando existe), então o host encontra
 * o schema ainda vazio e cria o próprio histórico normalmente. Usa as credenciais
 * {@code spring.flyway.url/user/password} do host quando presentes — normalmente
 * um papel com DDL, distinto do papel de runtime (RLS). Desligue com
 * {@code jp.billing.manage-schema=false} se o schema for gerenciado por fora.
 *
 * <p>O bean não é do tipo {@link Flyway} de propósito: um {@code @Bean Flyway}
 * dispararia o {@code @ConditionalOnMissingBean(Flyway.class)} do Boot e
 * suprimiria o Flyway do próprio host.
 */
@AutoConfiguration(
        afterName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
        })
@ConditionalOnClass(Flyway.class)
@ConditionalOnProperty(prefix = "jp.billing", name = "manage-schema", matchIfMissing = true)
public class BillingFlywayAutoConfiguration {

    static final String LOCATION = "classpath:billing/migration";
    static final String HISTORY_TABLE = "flyway_schema_history_billing";
    static final String MIGRATOR_BEAN = "billingSchemaMigrator";
    static final String HOST_FLYWAY_INITIALIZER = "flywayInitializer";

    @Bean(MIGRATOR_BEAN)
    BillingSchemaMigrator billingSchemaMigrator(DataSource dataSource, Environment environment) {
        return new BillingSchemaMigrator(dataSource, environment);
    }

    /**
     * Garante que a validação do Hibernate ({@code ddl-auto: validate}) só corre
     * depois que as tabelas {@code billing_*} existem.
     */
    @Bean
    static EntityManagerFactoryDependsOnPostProcessor billingSchemaMigratorEmfDependsOn() {
        return new EntityManagerFactoryDependsOnPostProcessor(MIGRATOR_BEAN);
    }

    /**
     * Faz o migrador da lib rodar depois do Flyway do host — mas só se o host
     * tiver um (dependência solta, resolvida em tempo de post-processing).
     */
    @Bean
    static BeanFactoryPostProcessor billingSchemaMigratorAfterHostFlyway() {
        return beanFactory -> {
            if (!beanFactory.containsBeanDefinition(MIGRATOR_BEAN)
                    || !beanFactory.containsBeanDefinition(HOST_FLYWAY_INITIALIZER)) {
                return;
            }
            var definition = beanFactory.getBeanDefinition(MIGRATOR_BEAN);
            var dependsOn = new ArrayList<String>();
            if (definition.getDependsOn() != null) {
                Collections.addAll(dependsOn, definition.getDependsOn());
            }
            if (!dependsOn.contains(HOST_FLYWAY_INITIALIZER)) {
                dependsOn.add(HOST_FLYWAY_INITIALIZER);
            }
            definition.setDependsOn(dependsOn.toArray(String[]::new));
        };
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
