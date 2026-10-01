package com.example;

import fr.universalserverloader.api.UniversalAddon;
import fr.universalserverloader.api.UniversalCommand;
import fr.universalserverloader.api.UniversalContext;

public final class ExampleAddon implements UniversalAddon {
    private UniversalContext context;

    public void onEnable(UniversalContext context) {
        this.context = context;
        context.log("Example Addon activé. Dossier de données: " + context.dataFolder());
        context.registerCommand("hello", new UniversalCommand() {
            public void execute(String[] arguments) {
                ExampleAddon.this.context.log("Bonjour depuis UniversalAddon ! Arguments: " + java.util.Arrays.toString(arguments));
            }
        });
    }

    public void onDisable() {
        if (context != null) context.log("Example Addon désactivé proprement.");
    }
}
