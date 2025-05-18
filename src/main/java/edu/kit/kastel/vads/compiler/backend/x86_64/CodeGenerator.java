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
    private static final String SPILL_REG = "%r11d";

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

    // TODO: Implement graph tiling and proper interference so we don't have to
    // exclude registers from being used for useful thingies.
    private void generateForGraph(IrGraph graph, StringBuilder builder, Map<Node, Register> registers, String source) {
        Set<Node> visited = new HashSet<>();
        scan(graph.endBlock(), visited, builder, registers, source);
    }

    private void scan(Node node, Set<Node> visited, StringBuilder builder, Map<Node, Register> registers,
            String source) {

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

    private static void emitDebugInfo(StringBuilder builder, Map<Node, Register> registers, Node node, String source) {

        // skip debug info for these nodes
        if (node instanceof Phi || node instanceof Block || node instanceof ProjNode || node instanceof StartNode) {
            return;
        }

        if (!(node.debugInfo() instanceof DebugInfo.SourceInfo sourceInfo)) {
            builder.repeat(INDENT, 1).append("# No source info 😢\n");
            return;
        }

        builder.repeat(INDENT, 1).append("# %s\n".formatted(sourceInfo.span()));
        sourceInfo.span().fromSource(source).lines().map(String::trim)
                .forEach(line -> builder.repeat(INDENT, 1).append("# %s\n".formatted(line)));
    }

    private static void emitConstAssign(StringBuilder builder, Map<Node, Register> registers, ConstIntNode node) {
        var value = node.value();
        var dst = registers.get(node);

        builder.repeat(INDENT, 1).append("movl $%d, %s\n".formatted(value, dst));
    }

    private static void emitReturn(StringBuilder builder, Map<Node, Register> registers, ReturnNode node) {
        var src = registers.get(NodeSupport.predecessorSkipProj(node, ReturnNode.RESULT));

        builder.repeat(INDENT, 1).append("movl %s, %s\n".formatted(src, "%eax"));
    }

    private static void emitBinaryOp(StringBuilder builder, Map<Node, Register> registers, BinaryOperationNode node,
            String opcode) {

        var lhs = registers.get(NodeSupport.predecessorSkipProj(node, BinaryOperationNode.LEFT));
        var rhs = registers.get(NodeSupport.predecessorSkipProj(node, BinaryOperationNode.RIGHT));
        var dest = registers.get(node);

        switch (opcode) {
            case "addl", "subl", "imull" -> {
                // TODO: I'm not happy with this, but at least it works
                // Either I'm doing something wrong during register allocation
                // or I just have to implement the special case `t1 <- t1 [op] t2`
                // where I don't need the extra register.
                //
                // But then this should be solved when I come around to implement tree tiling
                // from the Modern Compiler Implementation book.
                builder.repeat(INDENT, 1).append("movl %s, %s\n".formatted(lhs, SPILL_REG));
                builder.repeat(INDENT, 1).append("%s %s, %s\n".formatted(opcode, rhs, SPILL_REG));
                builder.repeat(INDENT, 1).append("movl %s, %s\n".formatted(SPILL_REG, dest));
            }
            case "idivl", "imodl" -> {
                // Sign-extend EAX into EDX:EAX
                // https://faydoc.tripod.com/cpu/cdq.htm
                String resultReg = opcode == "idivl" ? "%eax" : "%edx";
                builder.repeat(INDENT, 1).append("movl %s, %s\n".formatted(lhs, "%eax"));
                builder.repeat(INDENT, 1).append("cdq\n");
                builder.repeat(INDENT, 1).append("idivl %s\n".formatted(rhs));
                builder.repeat(INDENT, 1).append("movl %s, %s\n".formatted(resultReg, dest));
            }
        }
    }
}
