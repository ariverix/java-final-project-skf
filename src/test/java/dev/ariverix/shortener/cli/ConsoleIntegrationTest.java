package dev.ariverix.shortener.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ariverix.shortener.Config;
import dev.ariverix.shortener.core.LinkService;
import dev.ariverix.shortener.infra.FileRepository;
import dev.ariverix.shortener.infra.RandomCodes;
import dev.ariverix.shortener.infra.Session;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConsoleIntegrationTest {
    @TempDir
    Path directory;

    @Test
    void createsListsAndShowsHelp() throws Exception {
        Config config = new Config(Duration.ofHours(1), 2, 10, 1,
                directory.resolve("links.properties"), directory.resolve("session.txt"),
                "short://");
        LinkService links = new LinkService(new FileRepository(config.dataFile()),
                new RandomCodes(), config, Clock.systemUTC(), ignored -> { });
        String commands = "help\ncreate https://example.org 2\nlist\nnotices\nexit\n";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (Scanner input = new Scanner(commands);
                PrintStream printer = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            new Console(links, config, uri -> { }, new Session(config.sessionFile()),
                    input, printer).run();
        }
        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Короткая ссылка: short://"));
        assertTrue(text.contains("https://example.org"));
        assertTrue(text.contains("Нет уведомлений."));
        assertTrue(text.contains("Работа завершена."));
    }
}
