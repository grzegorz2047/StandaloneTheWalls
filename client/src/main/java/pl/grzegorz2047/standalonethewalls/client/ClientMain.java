package pl.grzegorz2047.standalonethewalls.client;

/** Desktop and headless-smoke client process entry point. */
public final class ClientMain {
    private ClientMain() {
        throw new AssertionError("No instances");
    }

    public static void main(String[] args) {
        int exitCode = ClientLauncher.run(args);
        if (exitCode != ClientLauncher.EXIT_OK) {
            System.exit(exitCode);
        }
    }
}
