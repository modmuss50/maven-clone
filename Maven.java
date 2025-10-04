
/**
 * A script to clone the files for a specified package from maven central
 *
 * Usage:
 * java Maven.java com.group:artifact:version
 * java Maven.java com.group:artifact
 *
 */
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Maven implements AutoCloseable {

    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final HttpClient client = HttpClient.newBuilder()
            .executor(executor)
            .build();

    public static void main(String[] args) throws Exception {
        try (var maven = new Maven()) {
            maven.run(args);
        }
    }

    void run(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Usage: java Maven.java com.group:artifact:version");
            System.out.println("Usage: java Maven.java com.group:artifact");
            return;
        }

        var split = args[0].split(":");

        List<Release> releases = split.length != 2
                ? List.of(Release.parse(args[0]))
                : getAllReleases(split[0], split[1]).join();

        for (var release : releases) {
            var files = getArtifactFiles(release).join();
            var futures = new ArrayList<CompletableFuture<Path>>();

            for (File file : files) {
                String url = file.url();
                Path outputFile = Path.of("files", file.release().path(), file.name());
                futures.add(downloadFile(url, outputFile));
            }

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }
        System.out.println("Done");
    }

    CompletableFuture<String> getString(String url) throws IOException {
        return client.sendAsync(
                HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(HttpResponse::body);
    }

    CompletableFuture<String> getIndexString(Release release) throws IOException {
        return getString(release.url());
    }

    CompletableFuture<List<File>> getArtifactFiles(Release release) throws IOException {
        return getIndexString(release).thenApply(s -> {
            List<File> urls = new ArrayList<>();
            Matcher matcher = Pattern.compile("href=\"([^\"]+\\.*.)").matcher(s);
            while (matcher.find()) {
                if (matcher.group(1).startsWith("../")) {
                    continue;
                }
                String filename = matcher.group(1).substring(0, matcher.group(1).length() - 1);
                urls.add(new File(release, filename));
            }

            if (urls.isEmpty()) {
                throw new IllegalArgumentException("No files found for " + release);
            }

            return urls;
        });
    }

    CompletableFuture<Path> downloadFile(String url, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        return client.sendAsync(
                HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofFile(outputFile)
        )
                .thenApply(response -> {
                    System.out.println("Downloaded " + url);
                    return response;
                })
                .thenApply(HttpResponse::body);
    }

    CompletableFuture<List<Release>> getAllReleases(String group, String artifact) throws IOException {
        return getString(MAVEN_CENTRAL + group.replace(".", "/") + "/" + artifact + "/maven-metadata.xml")
                .thenApply(s -> {
                    List<Release> releases = new ArrayList<>();
                    Matcher matcher = Pattern.compile("<version>([^<]+)</version>").matcher(s);
                    while (matcher.find()) {
                        releases.add(new Release(group, artifact, matcher.group(1)));
                    }
                    return releases;
                });
    }

    @Override
    public void close() throws Exception {
        executor.close();
        client.close();
    }

    record Release(String group, String artifact, String version) {

        static Release parse(String s) {
            String[] parts = s.split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid package format");
            }
            return new Release(parts[0], parts[1], parts[2]);
        }

        public String url() {
            return MAVEN_CENTRAL + group.replace(".", "/") + "/" + artifact + "/" + version + "/";
        }

        public String path() {
            return group.replace(".", "/") + "/" + artifact + "/" + version + "/";
        }
    }

    record File(Release release, String name) {

        public String url() {
            return release().url() + name;
        }

        public String path() {
            return release().path() + name;
        }
    }
}
