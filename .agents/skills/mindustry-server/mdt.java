// Drives a live Mindustry server started through a toxopid MindustryExec task (runMindustryServer).
// Usage: java mdt.java <verb> [args]. Run without arguments for help.
//
// A detached "serve" daemon owns `./gradlew <task>` and its stdin, and listens on a unix socket in build/mdt/.
// Every other verb is a short-lived client. After each console command, the daemon sends a marker command
// (js "__MDT_<n>__") and returns the log lines produced until the marker comes back, so output capture is exact.

import java.io.*;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

public class Mdt {
    static final String USAGE = """
            usage: java mdt.java <verb> [args]
              start [--task T] [--ready REGEX] [--timeout S]  start the server, wait until ready, load preludes
              stop                                            stop the server (graceful, then forced)
              restart [start options]                         stop then start (picks up a rebuilt jar)
              status                                          running state, task, data dir
              cmd <console command> [--timeout S]             run a server console command, print its output
              js <script> [--timeout S]                       shorthand for: cmd js <script>
              jsfile <file.js> [--timeout S]                  run a multi-line js file (comments allowed)
              wait <regex> [--timeout S]                      wait for a log line matching regex, emitted since the last command
              until <js expression> [--timeout S]             poll the expression until it is true (for state changes that are not logged)
              log [n]                                         print the last n log lines (default 40)
            options can also live in <root>/.mdt/config.properties (task=..., ready=...)""";

    static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");
    static final Pattern SERVER_LOADED = Pattern.compile("Server loaded");
    static final String MARKER = "__MDT_";
    // console error output: logged errors and the exceptions the js command prints
    static final Pattern ERROR = Pattern.compile("\\[E]|(EcmaError|EvaluatorException|JavaScriptException|WrappedException|Exception):");

    static Path root, state, sock, logFile, pidFile, configDir;

    public static void main(String[] args) throws Exception {
        root = findRoot();
        state = root.resolve("build/mdt");
        configDir = root.resolve(".mdt");
        sock = state.resolve("ctl.sock");
        logFile = state.resolve("server.log");
        pidFile = state.resolve("daemon.pid");
        Files.createDirectories(state);

        if (args.length == 0) fail(USAGE);
        var verb = args[0];
        var opts = new Opts(Arrays.copyOfRange(args, 1, args.length));
        int code = switch (verb) {
            case "serve" -> { new Daemon(opts.get("task", null)).run(); yield 0; }
            case "start" -> start(opts);
            case "stop" -> stop();
            case "restart" -> { stop(); yield start(opts); }
            case "status" -> running() ? request("status", "") : print("not running", 1);
            case "cmd" -> request("cmd", opts.rest() + "\t" + opts.get("timeout", "30"));
            case "js" -> request("cmd", "js " + opts.rest() + "\t" + opts.get("timeout", "30"));
            case "jsfile" -> request("cmd", jsRun(Path.of(opts.rest())) + "\t" + opts.get("timeout", "30"));
            case "wait" -> request("wait", opts.rest() + "\t" + opts.get("timeout", "30"));
            case "until" -> until(opts.rest(), seconds(opts.get("timeout", "30")));
            case "log" -> log(opts.rest().isBlank() ? 40 : Integer.parseInt(opts.rest().trim()));
            default -> print(USAGE, 1);
        };
        System.exit(code);
    }

    // ---------------------------------------------------------------- client

    static int start(Opts opts) throws Exception {
        if (running()) return print("already running, use restart to reload it", 0);
        var config = loadConfig();
        var task = opts.get("task", config.getProperty("task"));
        if (task == null) task = discoverTask();
        if (task == null) return 1;

        var cmd = new ArrayList<String>();
        if (!isWindows() && new File("/usr/bin/setsid").exists()) cmd.add("/usr/bin/setsid");
        cmd.add(ProcessHandle.current().info().command().orElse("java"));
        cmd.addAll(List.of(selfPath().toString(), "serve", "--task", task));
        new ProcessBuilder(cmd)
                .directory(root.toFile())
                .redirectInput(ProcessBuilder.Redirect.from(new File(isWindows() ? "NUL" : "/dev/null")))
                .redirectErrorStream(true)
                .redirectOutput(state.resolve("daemon.log").toFile())
                .start();

        var deadline = Instant.now().plusSeconds(30);
        while (!running()) {
            if (Instant.now().isAfter(deadline)) return print("daemon did not start, see " + state.resolve("daemon.log"), 1);
            Thread.sleep(200);
        }
        var preludes = new ArrayList<String>();
        preludes.add(selfPath().resolveSibling("prelude.js").toString());
        var projectPrelude = configDir.resolve("prelude.js");
        if (Files.exists(projectPrelude)) preludes.add(projectPrelude.toString());
        return request("ready", String.join("\t",
                opts.get("timeout", "600"),
                opts.get("ready", config.getProperty("ready", "")),
                String.join(File.pathSeparator, preludes)));
    }

    static int stop() throws Exception {
        if (running()) return request("stop", "");
        // socket gone but a daemon may still be alive
        if (Files.exists(pidFile)) {
            long pid = Long.parseLong(Files.readString(pidFile).trim());
            ProcessHandle.of(pid).ifPresent(p -> { p.descendants().forEach(ProcessHandle::destroyForcibly); p.destroyForcibly(); });
            Files.deleteIfExists(pidFile);
            return print("killed stale daemon " + pid, 0);
        }
        return print("not running", 0);
    }

    static boolean running() {
        if (!Files.exists(sock)) return false;
        try (var ch = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            ch.connect(UnixDomainSocketAddress.of(sock));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Sends one request line, prints the response body. The first response line is OK or ERR. */
    static int request(String verb, String payload) throws IOException {
        if (!running()) return print("not running, use start first", 1);
        try (var ch = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            ch.connect(UnixDomainSocketAddress.of(sock));
            var out = new PrintWriter(new OutputStreamWriter(Channels.newOutputStream(ch), StandardCharsets.UTF_8), true);
            out.println(verb + "\t" + payload.replace("\r", "").replace("\n", " "));
            var in = new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8));
            var status = in.readLine();
            for (String line; (line = in.readLine()) != null; ) System.out.println(line);
            return "OK".equals(status) ? 0 : 1;
        }
    }

    static int until(String expression, Duration timeout) throws Exception {
        var deadline = Instant.now().plus(timeout);
        var probe = "js (function () { return (" + expression + ") ? \"UNTIL_TRUE\" : \"UNTIL_FALSE\"; })()";
        while (Instant.now().isBefore(deadline)) {
            var output = new ByteArrayOutputStream();
            var previous = System.out;
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            int code;
            try {
                code = request("cmd", probe + "\t10");
            } finally {
                System.setOut(previous);
            }
            var text = output.toString(StandardCharsets.UTF_8);
            if (code != 0) return print(text, 1);
            if (text.contains("UNTIL_TRUE")) return print("true: " + expression, 0);
            Thread.sleep(250);
        }
        return print("TIMEOUT after " + timeout.toSeconds() + "s, still false: " + expression, 1);
    }

    static int log(int n) throws IOException {
        if (!Files.exists(logFile)) return print("no log yet", 1);
        var lines = Files.readAllLines(logFile);
        lines.subList(Math.max(0, lines.size() - n), lines.size()).forEach(System.out::println);
        return 0;
    }

    /** Lists the server exec tasks of the build, picks the only one or asks for --task. */
    static String discoverTask() throws Exception {
        var proc = new ProcessBuilder(gradlew(), "-q", "--console=plain", "tasks", "--all")
                .directory(root.toFile()).redirectErrorStream(true).start();
        var output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        proc.waitFor();
        var found = new ArrayList<String>();
        var matcher = Pattern.compile("(?m)^([\\w.:-]*:)?(run\\w*[Ss]erver\\w*)\\b").matcher(output);
        while (matcher.find()) found.add(":" + (matcher.group(1) == null ? "" : matcher.group(1)) + matcher.group(2));
        if (found.size() == 1) return found.getFirst();
        if (found.isEmpty()) print("no run*Server task found, is toxopid applied? output:\n" + output, 1);
        else print("several server tasks found, pass --task or set task= in " + configDir.resolve("config.properties") + ":\n  " + String.join("\n  ", found), 1);
        return null;
    }

    static Properties loadConfig() throws IOException {
        var props = new Properties();
        var file = configDir.resolve("config.properties");
        if (Files.exists(file)) try (var reader = Files.newBufferedReader(file)) { props.load(reader); }
        return props;
    }

    // ---------------------------------------------------------------- daemon

    static final class Daemon {
        final String task;
        final List<String> lines = new ArrayList<>();
        final Instant startedAt = Instant.now();
        Process gradle;
        Writer stdin;
        ServerSocketChannel server;
        volatile boolean dead;
        int seq, lastCommandStart;
        String dataDir = "?";
        // the server jvm is forked by the gradle daemon, so it is not a descendant of the gradle client
        long serverPid = -1;

        Daemon(String task) { this.task = task; }

        void run() throws Exception {
            Files.writeString(pidFile, Long.toString(ProcessHandle.current().pid()));
            Files.deleteIfExists(sock);
            server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            server.bind(UnixDomainSocketAddress.of(sock));

            gradle = new ProcessBuilder(gradlew(), "-q", "--console=plain", task)
                    .directory(root.toFile()).redirectErrorStream(true).start();
            stdin = new OutputStreamWriter(gradle.getOutputStream(), StandardCharsets.UTF_8);
            var log = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8);
            Thread.ofPlatform().daemon().start(() -> pump(log));

            try {
                while (true) {
                    try (var ch = server.accept()) { handle(ch); }
                    if (dead) break;
                }
            } catch (ClosedChannelException ignored) {
                // gradle exited
            } finally {
                Files.deleteIfExists(sock);
                Files.deleteIfExists(pidFile);
            }
        }

        void pump(Writer log) {
            try (var reader = new BufferedReader(new InputStreamReader(gradle.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null; ) {
                    line = ANSI.matcher(line).replaceAll("");
                    if (!line.contains(MARKER)) {
                        log.write(line + "\n");
                        log.flush();
                    }
                    synchronized (lines) { lines.add(line); lines.notifyAll(); }
                }
            } catch (IOException ignored) {
            }
            dead = true;
            synchronized (lines) { lines.notifyAll(); }
            try { server.close(); } catch (IOException ignored) { }
        }

        void handle(SocketChannel ch) throws IOException {
            var in = new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8));
            var out = new PrintWriter(new OutputStreamWriter(Channels.newOutputStream(ch), StandardCharsets.UTF_8), true);
            var request = in.readLine();
            if (request == null) return; // liveness probe
            var parts = request.split("\t", -1);
            try {
                var result = switch (parts[0]) {
                    case "cmd" -> command(parts[1], seconds(parts[2]));
                    case "wait" -> waitFor(Pattern.compile(parts[1]), lastCommandStart, seconds(parts[2]));
                    case "ready" -> ready(seconds(parts[1]), parts[2], parts[3]);
                    case "status" -> Result.ok("task: " + task + "\nserver pid: " + serverPid + "\nuptime: "
                            + Duration.between(startedAt, Instant.now()).toSeconds() + "s\ndata dir: " + dataDir + "\nlog: " + logFile);
                    case "stop" -> stopServer();
                    default -> Result.err("unknown request " + parts[0]);
                };
                out.println(result.ok ? "OK" : "ERR");
                out.print(result.body);
                if (!result.body.isEmpty() && !result.body.endsWith("\n")) out.println();
                out.flush();
            } catch (Exception e) {
                out.println("ERR");
                out.println(e);
            }
        }

        /** Sends a console command, returns the log lines emitted until the marker comes back. */
        Result command(String command, Duration timeout) throws IOException, InterruptedException {
            if (dead) return Result.err("server is not running\n" + tail(30));
            int id = ++seq;
            int from;
            synchronized (lines) { from = lines.size(); }
            lastCommandStart = from;
            // the marker is built by concatenation so the command text itself never matches
            stdin.write(command + "\njs \"" + MARKER + "\" + " + id + " + \"__\"\n");
            stdin.flush();
            var marker = MARKER + id + "__";
            var deadline = Instant.now().plus(timeout);
            synchronized (lines) {
                int scanned = from;
                while (true) {
                    for (; scanned < lines.size(); scanned++) {
                        if (lines.get(scanned).contains(marker)) {
                            var output = join(from, scanned);
                            return new Result(!ERROR.matcher(output).find(), output);
                        }
                    }
                    if (dead) return Result.err(join(from, lines.size()) + "\nserver exited");
                    long left = Duration.between(Instant.now(), deadline).toMillis();
                    if (left <= 0) return Result.err(join(from, lines.size()) + "\nTIMEOUT after " + timeout.toSeconds() + "s");
                    lines.wait(left);
                }
            }
        }

        Result waitFor(Pattern regex, int from, Duration timeout) throws InterruptedException {
            var deadline = Instant.now().plus(timeout);
            synchronized (lines) {
                int scanned = from;
                while (true) {
                    for (; scanned < lines.size(); scanned++) {
                        if (regex.matcher(lines.get(scanned)).find()) return Result.ok(lines.get(scanned));
                    }
                    if (dead) return Result.err("server exited\n" + tail(30));
                    long left = Duration.between(Instant.now(), deadline).toMillis();
                    if (left <= 0) return Result.err("TIMEOUT waiting for: " + regex + "\n" + tail(20));
                    lines.wait(left);
                }
            }
        }

        Result ready(Duration timeout, String readyRegex, String preludes) throws Exception {
            var loaded = waitFor(SERVER_LOADED, 0, timeout);
            if (!loaded.ok) return Result.err("server failed to start\n" + tail(40));
            if (!readyRegex.isBlank()) {
                var ready = waitFor(Pattern.compile(readyRegex), 0, timeout);
                if (!ready.ok) return ready;
            }
            var body = new StringBuilder("task: " + task + "\n");
            for (var prelude : preludes.split(File.pathSeparator)) {
                var result = command(jsRun(Path.of(prelude)), Duration.ofSeconds(30));
                body.append("prelude ").append(prelude).append(": ").append(result.body.strip()).append('\n');
                if (!result.ok) return Result.err(body.toString());
            }
            var dir = command("js String(Vars.dataDirectory.absolutePath())", Duration.ofSeconds(30));
            if (dir.ok) dataDir = lastValue(dir.body);
            var pid = command("js String(java.lang.ProcessHandle.current().pid())", Duration.ofSeconds(30));
            if (pid.ok) serverPid = Long.parseLong(lastValue(pid.body));
            body.append("data dir: ").append(dataDir).append("\nlog: ").append(logFile);
            return Result.ok(body.toString());
        }

        Result stopServer() throws Exception {
            if (!dead) {
                stdin.write("exit\n");
                stdin.flush();
                if (!gradle.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
                    var server = ProcessHandle.of(serverPid);
                    server.ifPresent(ProcessHandle::destroyForcibly);
                    gradle.descendants().forEach(ProcessHandle::destroyForcibly);
                    gradle.destroyForcibly();
                    gradle.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                    server.ifPresent(p -> p.onExit().orTimeout(10, java.util.concurrent.TimeUnit.SECONDS).join());
                    dead = true;
                    return Result.ok("stopped (forced)");
                }
            }
            dead = true;
            return Result.ok("stopped");
        }

        String join(int from, int to) {
            synchronized (lines) {
                return String.join("\n", lines.subList(from, Math.min(to, lines.size())).stream()
                        .filter(line -> !line.contains(MARKER)).toList());
            }
        }

        String tail(int n) {
            synchronized (lines) { return join(Math.max(0, lines.size() - n), lines.size()); }
        }
    }

    /** The value printed by a js command, without the log prefix. */
    static String lastValue(String output) {
        var lines = output.strip().split("\n");
        return lines[lines.length - 1].replaceAll("^.*\\[I] ", "");
    }

    record Result(boolean ok, String body) {
        static Result ok(String body) { return new Result(true, body); }
        static Result err(String body) { return new Result(false, body); }
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Wraps a multi-line script into a single console line. The script is compiled as a real top level script in
     * the console scope (not through eval, which breaks closures on older Rhino), so errors point to file:line.
     */
    static String jsRun(Path file) throws IOException {
        var script = Files.readString(file);
        var sb = new StringBuilder("js (function () { var s = Vars.mods.getScripts(); return s.context.evaluateString(s.scope, \"");
        for (char c : script.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> { }
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append("\", \"").append(file.getFileName()).append("\", 1); })()").toString();
    }

    static Path findRoot() {
        for (var dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve("gradlew")) || Files.exists(dir.resolve("gradlew.bat"))) return dir;
        }
        fail("no gradlew found in the current directory or its parents");
        return null;
    }

    static Path selfPath() {
        var args = ProcessHandle.current().info().arguments().orElse(new String[0]);
        for (var arg : args) if (arg.endsWith("mdt.java")) return Path.of(arg).toAbsolutePath();
        fail("run mdt.java as a source file: java path/to/mdt.java <verb>");
        return null;
    }

    static String gradlew() { return root.resolve(isWindows() ? "gradlew.bat" : "gradlew").toString(); }

    static boolean isWindows() { return System.getProperty("os.name").toLowerCase().contains("win"); }

    static Duration seconds(String value) { return Duration.ofSeconds(Long.parseLong(value.trim())); }

    static int print(String message, int code) {
        (code == 0 ? System.out : System.err).println(message);
        return code;
    }

    static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }

    /** Positional words plus --key value options. */
    static final class Opts {
        final Map<String, String> options = new HashMap<>();
        final List<String> positional = new ArrayList<>();

        Opts(String[] args) {
            for (int i = 0; i < args.length; i++) {
                if (args[i].startsWith("--") && i + 1 < args.length) options.put(args[i].substring(2), args[++i]);
                else positional.add(args[i]);
            }
        }

        String get(String key, String fallback) { return options.getOrDefault(key, fallback); }

        String rest() { return String.join(" ", positional); }
    }
}
