package dev.ariverix.shortener;

import dev.ariverix.shortener.cli.Console;
import dev.ariverix.shortener.core.LinkService;
import dev.ariverix.shortener.infra.DesktopBrowser;
import dev.ariverix.shortener.infra.FileRepository;
import dev.ariverix.shortener.infra.RandomCodes;
import dev.ariverix.shortener.infra.Session;
import java.io.IOException;
import java.time.Clock;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        try {
            Config config = Config.load();
            LinkService links = new LinkService(new FileRepository(config.dataFile()),
                    new RandomCodes(), config, Clock.systemUTC(), System.out::println);

            ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "expired-links-cleanup");
                thread.setDaemon(true);
                return thread;
            });
            cleaner.scheduleWithFixedDelay(() -> {
                try {
                    links.cleanup();
                } catch (IOException e) {
                    System.err.println("Ошибка очистки ссылок: " + e.getMessage());
                }
            }, config.cleanupSeconds(), config.cleanupSeconds(), TimeUnit.SECONDS);

            try (Scanner scanner = new Scanner(System.in)) {
                new Console(links, config, new DesktopBrowser(),
                        new Session(config.sessionFile()), scanner, System.out).run();
            } finally {
                cleaner.shutdownNow();
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Не удалось запустить сервис: " + e.getMessage());
            System.exit(1);
        }
    }
}
