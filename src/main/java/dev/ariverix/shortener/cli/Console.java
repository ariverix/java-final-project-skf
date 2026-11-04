package dev.ariverix.shortener.cli;

import dev.ariverix.shortener.Config;
import dev.ariverix.shortener.core.Browser;
import dev.ariverix.shortener.core.Link;
import dev.ariverix.shortener.core.LinkService;
import dev.ariverix.shortener.core.Notice;
import dev.ariverix.shortener.infra.Session;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

public final class Console {
    private final LinkService links;
    private final Config config;
    private final Browser browser;
    private final Session session;
    private final Scanner scanner;
    private final PrintStream out;
    private UUID currentUser;

    public Console(LinkService links, Config config, Browser browser, Session session,
                   Scanner scanner, PrintStream out) throws IOException {
        this.links = links;
        this.config = config;
        this.browser = browser;
        this.session = session;
        this.scanner = scanner;
        this.out = out;
        this.currentUser = session.load();
        if (currentUser != null && !links.known(currentUser)) {
            currentUser = null;
        }
    }

    public void run() {
        out.println("Сервис сокращения ссылок. Введите help для списка команд.");
        if (currentUser != null) {
            out.println("Ваш UUID: " + currentUser);
        }
        while (true) {
            out.print("> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                out.println("Работа завершена.");
                break;
            }
            try {
                execute(line);
            } catch (IOException | IllegalArgumentException | IllegalStateException
                    | SecurityException ex) {
                out.println("Ошибка: " + ex.getMessage());
            }
        }
    }

    private void execute(String line) throws IOException {
        String[] args = line.split("\\s+");
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "help" -> help();
            case "user" -> handleUser(args);
            case "create" -> {
                requireArgs(args, 2, "create <URL> [лимит]");
                int limit = args.length > 2 ? Integer.parseInt(args[2]) : config.defaultLimit();
                UUID user = ensureUser();
                Link link = links.create(user, args[1], limit);
                out.println("Короткая ссылка: " + links.shortUrl(link.code()));
                out.println("Переходов: " + link.limit() + ". Действует до " + date(link.expiresAt()));
            }
            case "open" -> {
                requireArgs(args, 2, "open <код>");
                open(args[1]);
            }
            case "list" -> {
                List<Link> items = links.list(user());
                if (items.isEmpty()) {
                    out.println("У вас пока нет ссылок.");
                }
                for (Link item : items) {
                    out.printf("%s  %d/%d  %s  %s%n", links.shortUrl(item.code()),
                            item.visits(), item.limit(), item.exhausted() ? "лимит исчерпан"
                                    : "активна", item.url());
                }
            }
            case "info" -> {
                requireArgs(args, 2, "info <код>");
                Link link = links.info(user(), args[1]);
                out.printf("URL: %s%nПереходов: %d/%d%nСоздана: %s%nИстекает: %s%n",
                        link.url(), link.visits(), link.limit(),
                        date(link.createdAt()), date(link.expiresAt()));
            }
            case "limit" -> {
                requireArgs(args, 3, "limit <код> <новый лимит>");
                Link link = links.setLimit(user(), args[1], Integer.parseInt(args[2]));
                out.println("Лимит обновлён: " + link.limit());
            }
            case "url" -> {
                requireArgs(args, 3, "url <код> <новый URL>");
                Link link = links.setUrl(user(), args[1], args[2]);
                out.println("Адрес обновлён: " + link.url());
            }
            case "delete" -> {
                requireArgs(args, 2, "delete <код>");
                links.delete(user(), args[1]);
                out.println("Ссылка удалена.");
            }
            case "notices" -> {
                List<Notice> notices = links.notices(user());
                if (notices.isEmpty()) {
                    out.println("Нет уведомлений.");
                }
                for (Notice notice : notices) {
                    out.println(date(notice.time()) + " " + notice.code() + ": " + notice.text());
                }
            }
            default -> {
                if (line.startsWith(config.prefix()) || line.matches("[A-Za-z0-9]{8,16}")) {
                    open(line);
                } else {
                    out.println("Неизвестная команда. Введите help.");
                }
            }
        }
    }

    private void open(String value) throws IOException {
        Link link = links.open(value, browser);
        out.println("Открыто в браузере: " + link.url());
        out.println("Использовано переходов: " + link.visits() + "/" + link.limit());
    }

    private void handleUser(String[] args) throws IOException {
        if (args.length == 1) {
            out.println(currentUser == null ? "UUID ещё не создан." : "Текущий UUID: " + currentUser);
        } else if (args.length == 2 && args[1].equals("new")) {
            UUID newUser = UUID.randomUUID();
            links.register(newUser);
            session.save(newUser);
            currentUser = newUser;
            out.println("Новый пользователь: " + newUser);
            out.println("Сохраните UUID: он необходим для управления вашими ссылками.");
        } else if (args.length == 3 && args[1].equals("use")) {
            UUID candidate = UUID.fromString(args[2]);
            if (!links.known(candidate)) {
                throw new IllegalArgumentException("Пользователь с таким UUID не найден");
            }
            session.save(candidate);
            currentUser = candidate;
            out.println("Активный пользователь: " + currentUser);
        } else {
            out.println("Команды: user, user new, user use <UUID>");
        }
    }

    private UUID ensureUser() throws IOException {
        if (currentUser == null) {
            UUID newUser = UUID.randomUUID();
            links.register(newUser);
            session.save(newUser);
            currentUser = newUser;
            out.println("Создан UUID пользователя: " + currentUser);
            out.println("Сохраните UUID для последующей работы со ссылками.");
        }
        return currentUser;
    }

    private UUID user() {
        if (currentUser == null) {
            throw new IllegalStateException("Сначала создайте ссылку или выполните user new");
        }
        return currentUser;
    }

    private void requireArgs(String[] args, int minimum, String usage) {
        if (args.length < minimum) {
            throw new IllegalArgumentException("Использование: " + usage);
        }
    }

    private static String date(long timestamp) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp));
    }

    private void help() {
        out.println("user                         показать UUID");
        out.println("user new                     создать другого пользователя");
        out.println("user use <UUID>              переключить пользователя");
        out.println("create <URL> [лимит]         сократить ссылку");
        out.println("open <код>                  открыть ссылку в браузере");
        out.println("list                         мои ссылки");
        out.println("info <код>                  сведения о ссылке");
        out.println("limit <код> <число>         изменить лимит");
        out.println("url <код> <новый URL>        изменить адрес");
        out.println("delete <код>                удалить ссылку");
        out.println("notices                      уведомления о недоступных ссылках");
        out.println("help                         справка");
        out.println("exit                         выход");
    }
}
