import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Parser {
    public enum CommandType {
        ARITHMETIC, PUSH, POP, LABEL, GOTO, IF, FUNCTION, RETURN, CALL
    }

    private static final List<String> ARITHMETIC_COMMANDS = Arrays.asList(
            "add", "sub", "neg", "eq", "gt", "lt", "and", "or", "not");

    private final List<String> commands = new ArrayList<>();
    private int index = -1;
    private String[] tokens;
    private CommandType currentType;

    public Parser(Path source) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String cleaned = stripComment(line);
                if (!cleaned.isEmpty()) {
                    commands.add(cleaned);
                }
            }
        }
    }

    public boolean hasMoreCommands() {
        return index + 1 < commands.size();
    }

    public void advance() {
        if (!hasMoreCommands()) {
            throw new IllegalStateException("No more VM commands");
        }
        index++;
        tokens = commands.get(index).split("\\s+");
        currentType = detectType(tokens[0]);
    }

    public CommandType commandType() {
        return currentType;
    }

    public String arg1() {
        if (currentType == CommandType.ARITHMETIC) {
            return tokens[0];
        }
        if (currentType == CommandType.RETURN) {
            throw new IllegalArgumentException("return has no arguments");
        }
        if (tokens.length < 2) {
            throw new IllegalArgumentException("Missing argument in: " + currentCommand());
        }
        return tokens[1];
    }

    public int arg2() {
        if (currentType != CommandType.PUSH && currentType != CommandType.POP
                && currentType != CommandType.FUNCTION && currentType != CommandType.CALL) {
            throw new IllegalArgumentException("Command has no second argument: " + currentCommand());
        }
        if (tokens.length < 3) {
            throw new IllegalArgumentException("Missing index in: " + currentCommand());
        }
        try {
            return Integer.parseInt(tokens[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid index in: " + currentCommand());
        }
    }

    private String currentCommand() {
        return index >= 0 ? commands.get(index) : "";
    }

    private static String stripComment(String line) {
        int commentIndex = line.indexOf("//");
        if (commentIndex >= 0) {
            line = line.substring(0, commentIndex);
        }
        return line.trim();
    }

    private static CommandType detectType(String operation) {
        if (ARITHMETIC_COMMANDS.contains(operation)) {
            return CommandType.ARITHMETIC;
        }
        switch (operation) {
            case "push":
                return CommandType.PUSH;
            case "pop":
                return CommandType.POP;
            case "label":
                return CommandType.LABEL;
            case "goto":
                return CommandType.GOTO;
            case "if-goto":
                return CommandType.IF;
            case "function":
                return CommandType.FUNCTION;
            case "return":
                return CommandType.RETURN;
            case "call":
                return CommandType.CALL;
            default:
                throw new IllegalArgumentException("Unknown VM command: " + operation);
        }
    }
}
