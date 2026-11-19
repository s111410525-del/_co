import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class VMTranslator {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java VMTranslator <path/to/FileName.vm | path/to/directory>");
            System.exit(1);
        }

        try {
            Path input = resolveInput(args[0]);
            Path output;
            if (Files.isDirectory(input)) {
                output = translateDirectory(input);
            } else {
                output = translateFile(input);
            }
            System.out.println("Wrote " + output);
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            System.err.println("Translation error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static Path translateFile(Path input) throws IOException {
        Path output = outputPath(input);
        try (CodeWriter writer = new CodeWriter(output, baseName(input))) {
            translateFileInto(input, writer);
        }
        return output;
    }

    private static Path translateDirectory(Path directory) throws IOException {
        List<Path> vmFiles = listVmFiles(directory);
        String directoryName = directory.getFileName().toString();
        Path output = directory.resolve(directoryName + ".asm");

        try (CodeWriter writer = new CodeWriter(output, directoryName)) {
            writer.writeBootstrap();
            for (Path vmFile : vmFiles) {
                writer.setFileName(baseName(vmFile));
                translateFileInto(vmFile, writer);
            }
        }
        return output;
    }

    private static void translateFileInto(Path input, CodeWriter writer) throws IOException {
        Parser parser = new Parser(input);
        while (parser.hasMoreCommands()) {
            parser.advance();
            Parser.CommandType type = parser.commandType();

            switch (type) {
                case ARITHMETIC:
                    writer.writeArithmetic(parser.arg1());
                    break;
                case PUSH:
                case POP:
                    writer.writePushPop(type, parser.arg1(), parser.arg2());
                    break;
                case LABEL:
                    writer.writeLabel(parser.arg1());
                    break;
                case GOTO:
                    writer.writeGoto(parser.arg1());
                    break;
                case IF:
                    writer.writeIf(parser.arg1());
                    break;
                case FUNCTION:
                    writer.writeFunction(parser.arg1(), parser.arg2());
                    break;
                case CALL:
                    writer.writeCall(parser.arg1(), parser.arg2());
                    break;
                case RETURN:
                    writer.writeReturn();
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported VM command: " + type);
            }
        }
    }

    private static List<Path> listVmFiles(Path directory) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".vm"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .forEach(files::add);
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException("No .vm files found in directory: " + directory);
        }
        return files;
    }

    private static Path resolveInput(String argument) {
        Path path = Paths.get(argument);
        if (!Files.exists(path) && !argument.toLowerCase().endsWith(".vm")) {
            path = Paths.get(argument + ".vm");
        }
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Input path not found: " + path);
        }
        if (!Files.isDirectory(path) && !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Input is neither a file nor a directory: " + path);
        }
        return path;
    }

    private static Path outputPath(Path input) {
        Path parent = input.getParent();
        String outputName = baseName(input) + ".asm";
        return parent == null ? Paths.get(outputName) : parent.resolve(outputName);
    }

    private static String baseName(Path input) {
        String name = input.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
