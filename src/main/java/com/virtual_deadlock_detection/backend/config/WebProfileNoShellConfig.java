package com.virtual_deadlock_detection.backend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Keeps the "web" profile a plain Spring Boot web server, with Spring Shell
 * fully out of the picture.
 *
 * <p>Neither {@code spring.shell.interactive.enabled=false} nor
 * {@code spring.shell.noninteractive.enabled=false} actually disables Spring
 * Shell:
 * <ul>
 *   <li>{@code interactive.enabled=false} just swaps the active
 *       {@code ShellRunner} implementation to {@code NonInteractiveShellRunner},
 *       which then requires a command-line argument (the command to run) and
 *       throws — killing the whole Spring Boot application, HTTP server
 *       included — when none is supplied. That's exactly what was happening:
 *       the web-profile process logged "Started ..." and then immediately
 *       died with "In non interactive mode, it expected to have at least one
 *       argument".</li>
 *   <li>{@code noninteractive.enabled} isn't a real Spring Shell property, so
 *       setting it did nothing.</li>
 * </ul>
 *
 * <p>{@code spring.autoconfigure.exclude} can't be used either — Spring
 * Shell's {@code org.springframework.shell.core.autoconfigure.ShellRunnerAutoConfiguration}
 * isn't registered through the standard
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * mechanism, so Boot rejects the exclude at startup with "not an
 * auto-configuration class".
 *
 * <p>The actual, documented way to stop Spring Shell from starting any
 * runner at all: {@code ShellRunnerAutoConfiguration#springShellApplicationRunner}
 * is annotated {@code @ConditionalOnMissingBean(ApplicationRunner.class)}.
 * By declaring our own harmless {@link ApplicationRunner} bean here — active
 * only for the "web" profile — that condition no longer matches, so Spring
 * Shell never registers any {@code ShellRunner} (interactive or
 * non-interactive) in this profile. The app just starts, binds Tomcat, and
 * stays up as a normal server for the CLI/agent to talk to.
 */
@Configuration
@Profile("web")
public class WebProfileNoShellConfig {

    @Bean
    public ApplicationRunner noOpApplicationRunner() {
        return (ApplicationArguments args) -> {
            // Intentionally does nothing. Its only job is to exist, so that
            // Spring Shell's own ApplicationRunner backs off.
        };
    }
}