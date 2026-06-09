package com.mzyupc.aredis.view;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConsolePanelCommandParsingTest {

    @Test
    void shouldTreatUnquotedLineBreaksAsArgumentSeparators() {
        ConsolePanel.ParsedConsoleCommand parsedCommand = ConsolePanel.parseCommand("SET\nuser:name\njack");

        Assertions.assertNotNull(parsedCommand);
        Assertions.assertEquals("SET", parsedCommand.getCommand());
        Assertions.assertEquals(2, parsedCommand.getArgs().size());
        Assertions.assertEquals("user:name", parsedCommand.getArgs().get(0));
        Assertions.assertEquals("jack", parsedCommand.getArgs().get(1));
    }

    @Test
    void shouldPreserveLineBreaksInsideQuotedArguments() {
        ConsolePanel.ParsedConsoleCommand parsedCommand = ConsolePanel.parseCommand(
                "EVAL \"return\\nredis.call('GET', KEYS[1])\" 1 demo:key"
                        .replace("\\n", "\n")
        );

        Assertions.assertNotNull(parsedCommand);
        Assertions.assertEquals("EVAL", parsedCommand.getCommand());
        Assertions.assertEquals(3, parsedCommand.getArgs().size());
        Assertions.assertEquals("return\nredis.call('GET', KEYS[1])", parsedCommand.getArgs().get(0));
        Assertions.assertEquals("1", parsedCommand.getArgs().get(1));
        Assertions.assertEquals("demo:key", parsedCommand.getArgs().get(2));
    }

    @Test
    void shouldKeepEmptyQuotedArguments() {
        ConsolePanel.ParsedConsoleCommand parsedCommand = ConsolePanel.parseCommand("SET demo:key \"\"");

        Assertions.assertNotNull(parsedCommand);
        Assertions.assertEquals("SET", parsedCommand.getCommand());
        Assertions.assertEquals(2, parsedCommand.getArgs().size());
        Assertions.assertEquals("demo:key", parsedCommand.getArgs().get(0));
        Assertions.assertEquals("", parsedCommand.getArgs().get(1));
    }
}

