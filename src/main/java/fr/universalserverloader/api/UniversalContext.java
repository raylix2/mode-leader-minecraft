package fr.universalserverloader.api;

import java.nio.file.Path;

public interface UniversalContext {
    void log(String message);
    Path dataFolder();
    void registerCommand(String name, UniversalCommand command);
}
