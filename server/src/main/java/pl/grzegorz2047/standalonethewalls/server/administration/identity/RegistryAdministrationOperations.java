package pl.grzegorz2047.standalonethewalls.server.administration.identity;

/** Replaceable boundary used by authorized identity administration commands. */
public interface RegistryAdministrationOperations {
    RegistryAdministrationResult verifySnapshot();

    RegistryAdministrationResult reloadRegistry();
}
