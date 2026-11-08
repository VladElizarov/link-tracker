package ru.tbank.load;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class LoadTest {

    private static final String GET = "GET /links";
    private static final String POST = "POST /links";
    private static final String DELETE = "DELETE /links";

    private static final int READS_PER_WRITE = 100;

    public static void main(String[] args) throws Exception {
        String label = arg(args, 0, "test");
        String baseUrl = arg(args, 1, "http://localhost:8081");
        int threads = Integer.parseInt(arg(args, 2, String.valueOf(2 * Runtime.getRuntime().availableProcessors())));
        int rampUpSec = Integer.parseInt(arg(args, 3, "60"));
        int stageSec = Integer.parseInt(arg(args, 4, "300"));
        int chats = Integer.parseInt(arg(args, 5, "1000"));
        String resultFile = arg(args, 6, "load-tests/results/" + label + ".md");

        System.out.printf("Тест '%s': %s, потоков %d, ramp-up %d с, stage %d с, чатов %d%n",
            label, baseUrl, threads, rampUpSec, stageSec, chats);

        HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        long start = System.currentTimeMillis();
        long stageStart = start + rampUpSec * 1000L;
        long stageEnd = stageStart + stageSec * 1000L;

        List<VirtualUser> users = new ArrayList<>();
        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            long startAt = start + (long) rampUpSec * 1000 * i / threads;
            VirtualUser user = new VirtualUser(i, client, baseUrl, chats, startAt, stageStart, stageEnd);
            users.add(user);
            Thread thread = new Thread(user, "user-" + i);
            workers.add(thread);
            thread.start();
        }
        printProgress(users, start, stageEnd);
        for (Thread thread : workers) {
            thread.join();
        }

        Map<String, Stats> total = new HashMap<>();
        for (VirtualUser user : users) {
            for (Map.Entry<String, Stats> entry : user.stats.entrySet()) {
                total.computeIfAbsent(entry.getKey(), k -> new Stats()).merge(entry.getValue());
            }
        }

        String report = Report.build(label, baseUrl, threads, rampUpSec, stageSec, chats, total, List.of(GET, POST, DELETE));
        System.out.println(report);
        Path path = Path.of(resultFile);
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, report);
        System.out.println("Результат сохранён: " + path.toAbsolutePath());
    }

    private static void printProgress(List<VirtualUser> users, long start, long end) {
        Thread progress = new Thread(() -> {
            while (System.currentTimeMillis() < end) {
                try {
                    Thread.sleep(30_000);
                } catch (InterruptedException e) {
                    return;
                }
                long requests = 0;
                for (VirtualUser user : users) {
                    requests += user.requestsDone;
                }
                System.out.printf("  прошло %d с, запросов: %d%n", (System.currentTimeMillis() - start) / 1000, requests);
            }
        });
        progress.setDaemon(true);
        progress.start();
    }

    private static String arg(String[] args, int index, String defaultValue) {
        return args.length > index && !args[index].isBlank() ? args[index] : defaultValue;
    }

    static class VirtualUser implements Runnable {

        private final int number;
        private final HttpClient client;
        private final String baseUrl;
        private final int chats;
        private final long startAt;
        private final long stageStart;
        private final long stageEnd;
        final Map<String, Stats> stats = new HashMap<>();
        volatile long requestsDone;

        private String addedLink;
        private long addedLinkChat;
        private int linkCounter;

        VirtualUser(int number, HttpClient client, String baseUrl, int chats, long startAt, long stageStart, long stageEnd) {
            this.number = number;
            this.client = client;
            this.baseUrl = baseUrl;
            this.chats = chats;
            this.startAt = startAt;
            this.stageStart = stageStart;
            this.stageEnd = stageEnd;
        }

        @Override
        public void run() {
            sleepUntil(startAt);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            while (System.currentTimeMillis() < stageEnd) {
                if (random.nextInt(READS_PER_WRITE + 1) == 0) {
                    write(random);
                } else {
                    long chatId = random.nextLong(1, chats + 1);
                    execute(GET, HttpRequest.newBuilder(URI.create(baseUrl + "/links"))
                        .header("Tg-Chat-Id", String.valueOf(chatId))
                        .GET());
                }
            }
            if (addedLink != null) {
                execute(null, deleteRequest(addedLinkChat, addedLink));
            }
        }

        private void write(ThreadLocalRandom random) {
            if (addedLink == null) {
                addedLinkChat = random.nextLong(1, chats + 1);
                addedLink = "https://github.com/load-test-" + number + "/new-repo-" + (++linkCounter);
                String body = "{\"link\": \"" + addedLink + "\"}";
                execute(POST, HttpRequest.newBuilder(URI.create(baseUrl + "/links"))
                    .header("Tg-Chat-Id", String.valueOf(addedLinkChat))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)));
            } else {
                execute(DELETE, deleteRequest(addedLinkChat, addedLink));
                addedLink = null;
            }
        }

        private HttpRequest.Builder deleteRequest(long chatId, String link) {
            return HttpRequest.newBuilder(URI.create(baseUrl + "/links"))
                .header("Tg-Chat-Id", String.valueOf(chatId))
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString("{\"link\": \"" + link + "\"}"));
        }

        private void execute(String name, HttpRequest.Builder builder) {
            HttpRequest request = builder.timeout(Duration.ofSeconds(10)).build();
            long begin = System.nanoTime();
            boolean measured = name != null && System.currentTimeMillis() >= stageStart;
            String outcome;
            int status;
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                status = response.statusCode();
                outcome = "HTTP " + status;
            } catch (IOException e) {
                status = -1;
                outcome = e.getClass().getSimpleName() + ": " + e.getMessage();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            long micros = (System.nanoTime() - begin) / 1000;
            requestsDone++;
            if (measured) {
                stats.computeIfAbsent(name, k -> new Stats()).add(micros, status, outcome);
            }
        }

        private static void sleepUntil(long time) {
            long wait = time - System.currentTimeMillis();
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
