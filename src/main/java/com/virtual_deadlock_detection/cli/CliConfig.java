package com.virtual_deadlock_detection.cli;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.shell.jline.PromptProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Shell configuration: sets a coloured ANSI prompt and a custom banner.
 */
@Configuration
public class CliConfig {

    /**
     * Replaces the default "shell:>" prompt with a branded one that shows the
     * application name in cyan.
     */
    @Bean
    public PromptProvider myPromptProvider() {
        return () -> new AttributedString(
                "vdd:> ",
                AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold()
        );
    }
}
