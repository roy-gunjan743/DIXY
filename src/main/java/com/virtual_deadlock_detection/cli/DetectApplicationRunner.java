package com.virtual_deadlock_detection.cli;

import com.sun.tools.attach.VirtualMachine;
import com.virtual_deadlock_detection.backend.entity.WaitEdgeEntity;
import com.virtual_deadlock_detection.backend.service.GraphService;
import com.virtual_deadlock_detection.backend.service.LockService;
import com.virtual_deadlock_detection.backend.service.ThreadService;
import com.virtual_deadlock_detection.backend.service.WaitEdgeService;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.model.GraphEdge;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "dixy.noninteractive", havingValue = "true")
public class DetectApplicationRunner implements ApplicationRunner {

    private final GraphService graphService;
    private final ThreadService threadService;
    private final LockService lockService;
    private final WaitEdgeService waitEdgeService;

    public DetectApplicationRunner(GraphService graphService, ThreadService threadService,
                                   LockService lockService, WaitEdgeService waitEdgeService) {
        this.graphService = graphService;
        this.threadService = threadService;
        this.lockService = lockService;
        this.waitEdgeService = waitEdgeService;
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        String pid = option(arguments, "pid");
        String jar = option(arguments, "jar");
        boolean json = arguments.containsOption("json");
        Process target = null;
        try {
            if (pid != null) {
                attach(pid);
            } else if (jar != null) {
                target = launch(jar);
            }
            boolean found = scan(json, target);
            if (found) {
                System.exit(1);
            }
        } catch (Exception exception) {
            System.err.println("DIXY detect failed: " + exception.getMessage());
            System.exit(2);
        } finally {
            if (target != null && target.isAlive()) {
                target.destroy();
            }
        }
    }

    private boolean scan(boolean json, Process target) throws InterruptedException {
        boolean found = false;
        while (target == null || target.isAlive()) {
            List<DeadlockCycle> cycles = graphService.detectDeadlocks();
            List<WaitEdgeEntity> edges = waitEdgeService.getAllEdges();
            if (json) {
                System.out.println(toJson(cycles));
            } else {
                System.out.printf("[%s] threads=%d locks=%d waits=%d cycles=%d%n",
                        Instant.now(), threadService.getAllThreads().size(),
                        lockService.getAllLocks().size(), edges.size(), cycles.size());
                printCycles(cycles, edges);
            }
            System.out.flush();
            if (!cycles.isEmpty()) {
                found = true;
                break;
            }
            TimeUnit.SECONDS.sleep(1);
        }
        return found;
    }

    private void attach(String pid) throws Exception {
        String agentPath = requireAgentPath();
        VirtualMachine vm = VirtualMachine.attach(pid);
        try {
            vm.loadAgent(agentPath, agentArguments("attached-" + pid));
        } finally {
            vm.detach();
        }
    }

    private Process launch(String jar) throws Exception {
        String agentPath = requireAgentPath();
        return new ProcessBuilder(javaCommand(), "-javaagent:" + agentPath + "="
                + agentArguments(Path.of(jar).getFileName().toString()), "-jar", jar)
                .inheritIO()
                .start();
    }

    private String requireAgentPath() {
        String path = System.getProperty("dixy.agent.path");
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("DIXY agent path is required for --pid or --jar.");
        }
        return path;
    }

    private String agentArguments(String appName) {
        return "serverUrl=" + System.getProperty("dixy.server.url", "http://localhost:8081") + ",apiKey="
                + System.getProperty("security.api-key", "dixy-local-dev-key-123")
                + ",appName=" + appName;
    }

    private String option(ApplicationArguments arguments, String name) {
        List<String> values = arguments.getOptionValues(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private void printCycles(List<DeadlockCycle> cycles, List<WaitEdgeEntity> edges) {
        for (int i = 0; i < cycles.size(); i++) {
            System.out.println("\u001B[1;31mALERT: DEADLOCK CYCLE " + (i + 1) + "\u001B[0m");
            for (GraphEdge edge : cycles.get(i).getEdgesInCycle()) {
                String lock = edges.stream()
                        .filter(item -> item.getWaitingThread() != null && item.getLock() != null
                                && item.getLock().getOwnerThread() != null
                                && edge.getSource().getLabel().equals(item.getWaitingThread().getThreadName())
                                && edge.getTarget().getLabel().equals(item.getLock().getOwnerThread().getThreadName()))
                        .map(item -> item.getLock().getLockId()).findFirst().orElse("unknown-lock");
                System.out.printf("  %s holds %s; %s waits for it%n", edge.getTarget().getLabel(), lock,
                        edge.getSource().getLabel());
            }
        }
    }

    private String toJson(List<DeadlockCycle> cycles) {
        StringBuilder json = new StringBuilder("{\"cycles\":[");
        for (int i = 0; i < cycles.size(); i++) {
            if (i > 0) json.append(',');
            json.append("{\"edges\":[");
            List<GraphEdge> cycleEdges = cycles.get(i).getEdgesInCycle();
            for (int j = 0; j < cycleEdges.size(); j++) {
                if (j > 0) json.append(',');
                GraphEdge edge = cycleEdges.get(j);
                json.append("{\"from\":\"").append(escape(edge.getSource().getLabel()))
                        .append("\",\"to\":\"").append(escape(edge.getTarget().getLabel())).append("\"}");
            }
            json.append("]}");
        }
        return json.append("]}").toString();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String javaCommand() {
        String javaHome = System.getenv("JAVA_HOME");
        return javaHome == null || javaHome.isBlank() ? "java" : Path.of(javaHome, "bin", "java").toString();
    }
}
