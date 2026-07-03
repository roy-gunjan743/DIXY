package com.virtual_deadlock_detection.backend.config;

import com.virtual_deadlock_detection.graph.registry.WaitForGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphConfiguration {

    @Bean
    public WaitForGraph waitForGraph() {
        return new WaitForGraph();
    }
}