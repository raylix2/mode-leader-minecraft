package fr.universalserverloader.cli;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class CliParser {
    public static final class Result {
        public final CliCommand command;
        public final List<String> serverArguments;
        Result(CliCommand command, List<String> serverArguments) { this.command = command; this.serverArguments = serverArguments; }
    }

    public Result parse(String[] args) {
        if (args.length == 0) return new Result(CliCommand.HELP, Collections.<String>emptyList());
        CliCommand command;
        try { command = CliCommand.valueOf(args[0].toUpperCase()); }
        catch (IllegalArgumentException e) { return new Result(CliCommand.HELP, Collections.<String>emptyList()); }
        int offset = args.length > 1 && "--".equals(args[1]) ? 2 : 1;
        return new Result(command, Arrays.asList(Arrays.copyOfRange(args, offset, args.length)));
    }
}
