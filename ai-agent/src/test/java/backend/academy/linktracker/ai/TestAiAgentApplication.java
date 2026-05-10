package backend.academy.linktracker.ai;

import org.springframework.boot.SpringApplication;

public class TestAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.from(AiAgentApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
