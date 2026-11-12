import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Assembler {

    private static final Map<String, String> COMP = new HashMap<>();
    private static final Map<String, String> DEST = new HashMap<>();
    private static final Map<String, String> JUMP = new HashMap<>();
    private static final Map<String, Integer> SYMBOLS = new HashMap<>();

    static {
        for (int i = 0; i <= 15; i++) {
            SYMBOLS.put("R" + i, i);
        }
        SYMBOLS.put("SP", 0);
        SYMBOLS.put("LCL", 1);
        SYMBOLS.put("ARG", 2);
        SYMBOLS.put("THIS", 3);
        SYMBOLS.put("THAT", 4);
        SYMBOLS.put("SCREEN", 16384);
        SYMBOLS.put("KBD", 24576);

        COMP.put("0", "0101010");
        COMP.put("1", "0111111");
        COMP.put("-1", "0111010");
        COMP.put("D", "0001100");
        COMP.put("A", "0110000");
        COMP.put("!D", "0001101");
        COMP.put("!A", "0110001");
        COMP.put("-D", "0001111");
        COMP.put("-A", "0110011");
        COMP.put("D+1", "0011111");
        COMP.put("A+1", "0110111");
        COMP.put("D-1", "0001110");
        COMP.put("A-1", "0110010");
        COMP.put("D+A", "0000010");
        COMP.put("D-A", "0010011");
        COMP.put("A-D", "0000111");
        COMP.put("D&A", "0000000");
        COMP.put("D|A", "0010101");
        COMP.put("M", "1110000");
        COMP.put("!M", "1110001");
        COMP.put("-M", "1110011");
        COMP.put("M+1", "1110111");
        COMP.put("M-1", "1110010");
        COMP.put("D+M", "1000010");
        COMP.put("D-M", "1010011");
        COMP.put("M-D", "1000111");
        COMP.put("D&M", "1000000");
        COMP.put("D|M", "1010101");

        DEST.put("", "000");
        DEST.put("M", "001");
        DEST.put("D", "010");
        DEST.put("MD", "011");
        DEST.put("A", "100");
        DEST.put("AM", "101");
        DEST.put("AD", "110");
        DEST.put("AMD", "111");

        JUMP.put("", "000");
        JUMP.put("JGT", "001");
        JUMP.put("JEQ", "010");
        JUMP.put("JGE", "011");
        JUMP.put("JLT", "100");
        JUMP.put("JNE", "101");
        JUMP.put("JLE", "110");
        JUMP.put("JMP", "111");
    }

    private Assembler() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Assembler <file.asm>");
            System.exit(1);
        }

        try {
            Path input = resolveInput(args[0]);
            List<String> lines = Files.readAllLines(input, StandardCharsets.UTF_8);
            List<String> instructions = firstPass(lines);
            String program = secondPass(instructions);
            Path output = resolveOutput(input);
            Files.writeString(output, program, StandardCharsets.UTF_8);
            System.out.println("Assembled " + input.getFileName() + " -> " + output.getFileName());
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println("Assembly error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static Path resolveInput(String argument) {
        Path path = Path.of(argument);
        if (!argument.toLowerCase().endsWith(".asm")) {
            path = Path.of(argument + ".asm");
        }
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("input file not found: " + path);
        }
        return path;
    }

    private static Path resolveOutput(Path input) {
        String fileName = input.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot >= 0 ? fileName.substring(0, dot) : fileName;
        return input.resolveSibling(baseName + ".hack");
    }

    private static List<String> firstPass(List<String> lines) {
        List<String> instructions = new ArrayList<>();
        int romAddress = 0;

        for (String rawLine : lines) {
            String line = stripComment(rawLine);
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("(")) {
                if (!line.endsWith(")")) {
                    throw new IllegalArgumentException("malformed label: " + line);
                }
                String label = line.substring(1, line.length() - 1).trim();
                if (label.isEmpty()) {
                    throw new IllegalArgumentException("empty label");
                }
                if (SYMBOLS.containsKey(label)) {
                    throw new IllegalArgumentException("duplicate symbol: " + label);
                }
                SYMBOLS.put(label, romAddress);
            } else {
                instructions.add(line);
                romAddress++;
            }
        }
        return instructions;
    }

    private static String secondPass(List<String> instructions) {
        StringBuilder program = new StringBuilder();
        int nextVariableAddress = 16;

        for (String instruction : instructions) {
            if (instruction.startsWith("@")) {
                String symbol = instruction.substring(1).trim();
                if (symbol.isEmpty()) {
                    throw new IllegalArgumentException("missing value after @");
                }
                int address;
                if (symbol.matches("\\d+")) {
                    address = Integer.parseInt(symbol);
                } else {
                    Integer value = SYMBOLS.get(symbol);
                    if (value == null) {
                        value = nextVariableAddress++;
                        SYMBOLS.put(symbol, value);
                    }
                    address = value;
                }
                if (address < 0 || address > 32767) {
                    throw new IllegalArgumentException("address out of range: " + address);
                }
                program.append(toBinary(address, 16)).append(System.lineSeparator());
            } else {
                program.append(assembleCInstruction(instruction)).append(System.lineSeparator());
            }
        }
        return program.toString();
    }

    private static String assembleCInstruction(String instruction) {
        String dest = "";
        String comp;
        String jump = "";
        String body = instruction;

        int equals = body.indexOf('=');
        if (equals >= 0) {
            dest = body.substring(0, equals).trim();
            body = body.substring(equals + 1).trim();
        }

        int semicolon = body.indexOf(';');
        if (semicolon >= 0) {
            jump = body.substring(semicolon + 1).trim();
            body = body.substring(0, semicolon).trim();
        }

        comp = body.replace(" ", "");

        String compBits = COMP.get(comp);
        if (compBits == null) {
            throw new IllegalArgumentException("invalid comp in instruction: " + instruction);
        }
        String destBits = DEST.get(dest);
        if (destBits == null) {
            throw new IllegalArgumentException("invalid dest in instruction: " + instruction);
        }
        String jumpBits = JUMP.get(jump);
        if (jumpBits == null) {
            throw new IllegalArgumentException("invalid jump in instruction: " + instruction);
        }

        return "111" + compBits + destBits + jumpBits;
    }

    private static String stripComment(String line) {
        int comment = line.indexOf("//");
        if (comment >= 0) {
            line = line.substring(0, comment);
        }
        return line.trim();
    }

    private static String toBinary(int value, int width) {
        String bits = Integer.toBinaryString(value);
        if (bits.length() > width) {
            throw new IllegalArgumentException("value does not fit in " + width + " bits: " + value);
        }
        return "0".repeat(width - bits.length()) + bits;
    }
}
