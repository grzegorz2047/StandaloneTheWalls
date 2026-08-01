package pl.grzegorz2047.standalonethewalls.server;

/** Headless dedicated-server process entry point. */
public final class ServerMain {
    private ServerMain() {
        throw new AssertionError("No instances");
    }

    public static void main(String[] args) {
        int exitCode = ServerLauncher.run(args);
        if (exitCode != ServerLauncher.EXIT_OK) {
            System.exit(exitCode);
        }
    }
}
