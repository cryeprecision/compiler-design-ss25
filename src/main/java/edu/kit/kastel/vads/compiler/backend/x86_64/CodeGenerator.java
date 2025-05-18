package edu.kit.kastel.vads.compiler.backend.x86_64;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.AddNode;
import edu.kit.kastel.vads.compiler.ir.node.BinaryOperationNode;
import edu.kit.kastel.vads.compiler.ir.node.Block;
import edu.kit.kastel.vads.compiler.ir.node.ConstIntNode;
import edu.kit.kastel.vads.compiler.ir.node.DivNode;
import edu.kit.kastel.vads.compiler.ir.node.ModNode;
import edu.kit.kastel.vads.compiler.ir.node.MulNode;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.node.Phi;
import edu.kit.kastel.vads.compiler.ir.node.ProjNode;
import edu.kit.kastel.vads.compiler.ir.node.ReturnNode;
import edu.kit.kastel.vads.compiler.ir.node.StartNode;
import edu.kit.kastel.vads.compiler.ir.node.SubNode;
import edu.kit.kastel.vads.compiler.ir.util.DebugInfo;
import edu.kit.kastel.vads.compiler.ir.util.NodeSupport;

public class CodeGenerator {
    private static final String INDENT = "    ";
    private static final String TEMPLATE = """
            .global main
            .global _main
            .text

            main:
                # exit(_main())
                call _main
                movq %rax, %rdi
                movq $0x3C, %rax
                syscall

            _main:
                pushq %rbp
                movq %rsp, %rbp

                {{GENERATED_CODE}}

                popq %rbp
                ret
            """;

    public String generateCode(List<IrGraph> program, String source) {
        if (program.size() > 1) {
            throw new UnsupportedOperationException("multiple graphs not supported yet");
        }

        // FIXME: This assumes that the program consists of a single graph
        StringBuilder builder = new StringBuilder();
        IrGraph graph = program.get(0);

        GatRegisterAllocator allocator = new GatRegisterAllocator();
        Map<Node, Register> registers = allocator.allocateRegisters(graph, source);

        generateForGraph(graph, builder, registers, source);
        return TEMPLATE.replace(INDENT + "{{GENERATED_CODE}}", builder.toString());
    }

    private void generateForGraph(IrGraph graph, StringBuilder builder,
            Map<Node, Register> registers, String source) {
        Set<Node> visited = new HashSet<>();
        scan(graph.endBlock(), visited, builder, registers, source);
    }

    private void scan(Node node, Set<Node> visited, StringBuilder builder,
            Map<Node, Register> registers, String source) {

        for (Node predecessor : node.predecessors()) {
            if (visited.add(predecessor)) {
                scan(predecessor, visited, builder, registers, source);
            }
        }

        emitDebugInfo(builder, registers, node, source);
        switch (node) {
            case AddNode add -> emitBinaryOp(builder, registers, add, "addl");
            case SubNode sub -> emitBinaryOp(builder, registers, sub, "subl");
            case MulNode mul -> emitBinaryOp(builder, registers, mul, "imull");
            case DivNode div -> emitBinaryOp(builder, registers, div, "idivl");
            case ModNode mod -> emitBinaryOp(builder, registers, mod, "imodl");
            case ReturnNode r -> emitReturn(builder, registers, r);
            case ConstIntNode c -> emitConstAssign(builder, registers, c);
            case Phi _ -> throw new UnsupportedOperationException("phi");
            case Block _,ProjNode _,StartNode _ -> {
                // do nothing, skip line break
                return;
            }
        }

        builder.append('\n');
    }

    private static void emitDebugInfo(StringBuilder builder, Map<Node, Register> registers,
            Node node, String source) {

        // skip debug info for these nodes
        switch (node) {
            case Phi _,Block _,ProjNode _,StartNode _ -> {
                return;
            }
            default -> {
                // no-op
            }
        }

        DebugInfo debugInfo = node.debugInfo();
        if (!(debugInfo instanceof DebugInfo.SourceInfo)) {
            builder.repeat(INDENT, 1).append("# ").append("No source info 😢").append("\n");
            return;
        }

        DebugInfo.SourceInfo sourceInfo = (DebugInfo.SourceInfo) debugInfo;
        List<String> sourceLines = sourceInfo.span().fromSource(source).lines().toList();

        builder.repeat(INDENT, 1).append("# ").append(sourceInfo.span()).append("\n");
        for (String line : sourceLines) {
            builder.repeat(INDENT, 1).append("# ").append(line.trim()).append("\n");
        }
    }

    private static void emitConstAssign(StringBuilder builder, Map<Node, Register> registers,
            ConstIntNode node) {
        var dst = registers.get(node);
        var value = node.value();

        builder.repeat(INDENT, 1).append("movl").append(" ").append("$").append(value).append(", ")
                .append(dst).append("\n");
    }

    private static void emitReturn(StringBuilder builder, Map<Node, Register> registers,
            ReturnNode node) {
        var src = registers.get(NodeSupport.predecessorSkipProj(node, ReturnNode.RESULT));

        builder.repeat(INDENT, 1).append("xorq %rax, %rax").append("\n");
        builder.repeat(INDENT, 1).append("movl").append(" ").append(src).append(", ").append("%eax")
                .append("\n");
    }

    private static void emitBinaryOp(StringBuilder builder, Map<Node, Register> registers,
            BinaryOperationNode node, String opcode) {
        var dest = registers.get(node);
        var left = registers.get(NodeSupport.predecessorSkipProj(node, BinaryOperationNode.LEFT));
        var right = registers.get(NodeSupport.predecessorSkipProj(node, BinaryOperationNode.RIGHT));

        switch (opcode) {
            case "addl", "subl", "imull" -> {
                builder.repeat(INDENT, 1).append("movl").append(" ").append(left).append(", ")
                        .append("%eax").append("\n");

                builder.repeat(INDENT, 1).append(opcode).append(" ").append(right).append(", ")
                        .append("%eax").append("\n");

                builder.repeat(INDENT, 1).append("movl").append(" ").append("%eax").append(", ")
                        .append(dest).append("\n");
            }
            case "idivl", "imodl" -> {
                builder.repeat(INDENT, 1).append("movl").append(" ").append(left).append(", ")
                        .append("%eax").append("\n");

                // Sign-extend EAX into EDX:EAX
                // https://faydoc.tripod.com/cpu/cdq.htm
                builder.repeat(INDENT, 1).append("cdq").append("\n");

                builder.repeat(INDENT, 1).append("idivl").append(" ").append(right).append("\n");

                switch (opcode) {
                    case "idivl" -> {
                        builder.repeat(INDENT, 1).append("movl").append(" ").append("%eax")
                                .append(", ").append(dest).append("\n");
                    }
                    case "imodl" -> {
                        builder.repeat(INDENT, 1).append("movl").append(" ").append("%edx")
                                .append(", ").append(dest).append("\n");
                    }
                }
            }
        }

    }
}
