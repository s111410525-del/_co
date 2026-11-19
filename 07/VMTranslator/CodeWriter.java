import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class CodeWriter implements Closeable {
    private final BufferedWriter writer;
    private String fileName;
    private String currentFunction;
    private int comparisonId;
    private int callId;
    private boolean closed;

    public CodeWriter(Path target, String fileName) throws IOException {
        this.writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8);
        setFileName(fileName);
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
        this.currentFunction = fileName;
    }

    public void writeArithmetic(String command) throws IOException {
        switch (command.toLowerCase()) {
            case "add":
                writeAdd();
                break;
            case "sub":
                writeSub();
                break;
            case "neg":
                writeNeg();
                break;
            case "eq":
                writeComparison("JEQ");
                break;
            case "gt":
                writeComparison("JGT");
                break;
            case "lt":
                writeComparison("JLT");
                break;
            case "and":
                writeAnd();
                break;
            case "or":
                writeOr();
                break;
            case "not":
                writeNot();
                break;
            default:
                throw new IllegalArgumentException("Unsupported arithmetic command: " + command);
        }
    }

    public void writePushPop(Parser.CommandType type, String segment, int index) throws IOException {
        requireNonNegative(index);
        if (type == Parser.CommandType.PUSH) {
            writePush(segment, index);
        } else if (type == Parser.CommandType.POP) {
            writePop(segment, index);
        } else {
            throw new IllegalArgumentException("Not a push/pop command: " + type);
        }
    }

    public void writeLabel(String label) throws IOException {
        emit("(" + scopedLabel(label) + ")");
    }

    public void writeGoto(String label) throws IOException {
        emit("@" + scopedLabel(label));
        emit("0;JMP");
    }

    public void writeIf(String label) throws IOException {
        popToD();
        emit("@" + scopedLabel(label));
        emit("D;JNE");
    }

    public void writeFunction(String functionName, int localCount) throws IOException {
        requireNonNegative(localCount);
        currentFunction = functionName;
        emit("(" + functionName + ")");
        for (int i = 0; i < localCount; i++) {
            emit("@SP");
            emit("A=M");
            emit("M=0");
            emit("@SP");
            emit("M=M+1");
        }
    }

    public void writeCall(String functionName, int argumentCount) throws IOException {
        requireNonNegative(argumentCount);
        String returnLabel = "RETURN_ADDRESS" + callId;
        callId++;

        emit("@" + returnLabel);
        emit("D=A");
        pushD();
        pushSegment("LCL");
        pushSegment("ARG");
        pushSegment("THIS");
        pushSegment("THAT");
        emit("@SP");
        emit("D=M");
        emit("@5");
        emit("D=D-A");
        emit("@" + argumentCount);
        emit("D=D-A");
        emit("@ARG");
        emit("M=D");
        emit("@SP");
        emit("D=M");
        emit("@LCL");
        emit("M=D");
        emit("@" + functionName);
        emit("0;JMP");
        emit("(" + returnLabel + ")");
    }

    public void writeReturn() throws IOException {
        emit("@LCL");
        emit("D=M");
        emit("@R13");
        emit("M=D");
        emit("@R13");
        emit("D=M");
        emit("@5");
        emit("A=D-A");
        emit("D=M");
        emit("@R14");
        emit("M=D");

        popToD();
        emit("@ARG");
        emit("A=M");
        emit("M=D");
        emit("@ARG");
        emit("D=M+1");
        emit("@SP");
        emit("M=D");

        restoreSegment("THAT");
        restoreSegment("THIS");
        restoreSegment("ARG");
        restoreSegment("LCL");

        emit("@R14");
        emit("A=M");
        emit("0;JMP");
    }

    public void writeBootstrap() throws IOException {
        emit("@256");
        emit("D=A");
        emit("@SP");
        emit("M=D");
        writeCall("Sys.init", 0);
    }

    private void pushSegment(String segment) throws IOException {
        emit("@" + segment);
        emit("D=M");
        pushD();
    }

    private void restoreSegment(String segment) throws IOException {
        emit("@R13");
        emit("AM=M-1");
        emit("D=M");
        emit("@" + segment);
        emit("M=D");
    }

    private String scopedLabel(String label) {
        return currentFunction + "$" + label;
    }

    private void writePush(String segment, int index) throws IOException {
        switch (segment) {
            case "constant":
                emit("@" + index);
                emit("D=A");
                break;
            case "local":
            case "argument":
            case "this":
            case "that":
                emit("@" + baseRegister(segment));
                emit("D=M");
                emit("@" + index);
                emit("A=D+A");
                emit("D=M");
                break;
            case "temp":
                requireRange(index, 0, 7, "temp");
                emit("@" + (5 + index));
                emit("D=M");
                break;
            case "pointer":
                requireRange(index, 0, 1, "pointer");
                emit("@" + (3 + index));
                emit("D=M");
                break;
            case "static":
                emit("@" + staticSymbol(index));
                emit("D=M");
                break;
            default:
                throw new IllegalArgumentException("Unsupported push segment: " + segment);
        }
        pushD();
    }

    private void writePop(String segment, int index) throws IOException {
        switch (segment) {
            case "constant":
                throw new IllegalArgumentException("Cannot pop into constant segment");
            case "local":
            case "argument":
            case "this":
            case "that":
                emit("@" + baseRegister(segment));
                emit("D=M");
                emit("@" + index);
                emit("D=D+A");
                emit("@R13");
                emit("M=D");
                popToD();
                emit("@R13");
                emit("A=M");
                emit("M=D");
                break;
            case "temp":
                requireRange(index, 0, 7, "temp");
                popToD();
                emit("@" + (5 + index));
                emit("M=D");
                break;
            case "pointer":
                requireRange(index, 0, 1, "pointer");
                popToD();
                emit("@" + (3 + index));
                emit("M=D");
                break;
            case "static":
                popToD();
                emit("@" + staticSymbol(index));
                emit("M=D");
                break;
            default:
                throw new IllegalArgumentException("Unsupported pop segment: " + segment);
        }
    }

    private void writeAdd() throws IOException {
        emit("@SP");
        emit("AM=M-1");
        emit("D=M");
        emit("A=A-1");
        emit("M=D+M");
    }

    private void writeSub() throws IOException {
        emit("@SP");
        emit("AM=M-1");
        emit("D=M");
        emit("A=A-1");
        emit("M=M-D");
    }

    private void writeNeg() throws IOException {
        emit("@SP");
        emit("A=M-1");
        emit("M=-M");
    }

    private void writeAnd() throws IOException {
        emit("@SP");
        emit("AM=M-1");
        emit("D=M");
        emit("A=A-1");
        emit("M=D&M");
    }

    private void writeOr() throws IOException {
        emit("@SP");
        emit("AM=M-1");
        emit("D=M");
        emit("A=A-1");
        emit("M=D|M");
    }

    private void writeNot() throws IOException {
        emit("@SP");
        emit("A=M-1");
        emit("M=!M");
    }

    private void writeComparison(String jump) throws IOException {
        String trueLabel = "IF_TRUE" + comparisonId;
        String endLabel = "IF_END" + comparisonId;
        comparisonId++;

        emit("@SP");
        emit("AM=M-1");
        emit("D=M");
        emit("A=A-1");
        emit("D=M-D");
        emit("@" + trueLabel);
        emit("D;" + jump);
        emit("@SP");
        emit("A=M-1");
        emit("M=0");
        emit("@" + endLabel);
        emit("0;JMP");
        emit("(" + trueLabel + ")");
        emit("@SP");
        emit("A=M-1");
        emit("M=-1");
        emit("(" + endLabel + ")");
    }

    private void pushD() throws IOException {
        emit("@SP");
        emit("A=M");
        emit("M=D");
        emit("@SP");
        emit("M=M+1");
    }

    private void popToD() throws IOException {
        emit("@SP");
        emit("AM=M-1");
        emit("D=M");
    }

    private String staticSymbol(int index) {
        return fileName + "." + index;
    }

    private static String baseRegister(String segment) {
        switch (segment) {
            case "local":
                return "LCL";
            case "argument":
                return "ARG";
            case "this":
                return "THIS";
            case "that":
                return "THAT";
            default:
                throw new IllegalArgumentException("Unsupported base segment: " + segment);
        }
    }

    private static void requireNonNegative(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Index must be non-negative: " + index);
        }
    }

    private static void requireRange(int index, int min, int max, String segment) {
        if (index < min || index > max) {
            throw new IllegalArgumentException(
                    segment + " index must be between " + min + " and " + max + ": " + index);
        }
    }

    private void emit(String line) throws IOException {
        writer.write(line);
        writer.newLine();
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        try {
            emit("(END)");
            emit("@END");
            emit("0;JMP");
        } finally {
            writer.close();
            closed = true;
        }
    }
}
