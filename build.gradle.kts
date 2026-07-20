plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.ben-manes.versions") version "0.54.0"
}

group = "ai.lab"
version = "0.0.1-SNAPSHOT"
description = "weeklyreport"

// Версии зависимостей, не управляемых Spring Boot BOM напрямую (см. отчёт о выборе версий в README.md)
val poiVersion = "5.5.1"
val telegramBotsVersion = "10.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    implementation("org.postgresql:postgresql")

    implementation("org.apache.poi:poi-ooxml:$poiVersion")

    implementation("org.telegram:telegrambots-springboot-longpolling-starter:$telegramBotsVersion")
    implementation("org.telegram:telegrambots-client:$telegramBotsVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Разовая генерация бинарной тестовой xlsx-фикстуры (см. TestFixtureGenerator) - запускать вручную
// при изменении структуры src/test/resources/fixtures/monthly_report_test.xlsx.
tasks.register<JavaExec>("generateTestFixtures") {
    group = "verification"
    description = "Перегенерировать src/test/resources/fixtures/monthly_report_test.xlsx"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ai.lab.weeklyreport.excel.support.TestFixtureGenerator")
}
