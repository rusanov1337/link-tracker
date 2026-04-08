package backend.academy.linktracker.bot;

import org.springframework.boot.SpringApplication;

public class TestBotApplication {

    static void main(String[] args) {
        SpringApplication.from(BotApplication::main).run(args);
    }
}
