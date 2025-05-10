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
import edu.kit.kastel.vads.compiler.ir.util.NodeSupport;

public class CodeGenerator {
    private static String INDENT = "    ";

    public String generateCode(List<IrGraph> program) {
        StringBuilder builder = new StringBuilder();
        for (IrGraph graph : program) {
            GatRegisterAllocator allocator = new GatRegisterAllocator();
            Map<Node, Register> registers = allocator.allocateRegisters(graph);

            builder.append("""
                    .global main
                    .global _main
                    .text

                    main:
                        call _main

                        # move the return value into the first argument for the syscall
                        movq %rax, %rdi
                        # move the exit syscall number into rax
                        movq $0x3C, %rax
                        syscall

                    _main:
                        pushq %rbp
                        movq %rsp, %rbp
                    """)
                    .append("\n");

            generateForGraph(graph, builder, registers);

            builder.append("""
                        popq %rbp
                        ret
                    """);
        }
        return builder.toString();
    }

    private void generateForGraph(IrGraph graph, StringBuilder builder, Map<Node, Register> registers) {
        Set<Node> visited = new HashSet<>();
        scan(graph.endBlock(), visited, builder, registers);
    }

    private void scan(Node node, Set<Node> visited, StringBuilder builder, Map<Node, Register> registers) {
        for (Node predecessor : node.predecessors()) {
            if (visited.add(predecessor)) {
                scan(predecessor, visited, builder, registers);
            }
        }

        switch (node) {
            case AddNode add -> binary(builder, registers, add, "addl");
            case SubNode sub -> binary(builder, registers, sub, "subl");
            case MulNode mul -> binary(builder, registers, mul, "imull");
            case DivNode div -> binary(builder, registers, div, "idivl");
            case ModNode mod -> binary(builder, registers, mod, "imodl");
            case ReturnNode r -> ret(builder, registers, r);
            case ConstIntNode c -> cnst(builder, registers, c);
            case Phi _ -> throw new UnsupportedOperationException("phi");
            case Block _,ProjNode _,StartNode _ -> {
                // do nothing, skip line break
                return;
            }
        }

        builder.append('\n');
    }

    private static void cnst(
            StringBuilder builder,
            Map<Node, Register> registers,
            ConstIntNode node) {
        var dst = registers.get(node);
        var value = node.value();

        builder.repeat(INDENT, 1)
                .append("movl").append(" ")
                .append("$").append(value).append(", ")
                .append(dst).append("\n");
    }

    private static void ret(
            StringBuilder builder,
            Map<Node, Register> registers,
            ReturnNode node) {
        var src = registers.get(NodeSupport.predecessorSkipProj(node, ReturnNode.RESULT));

        builder.repeat(INDENT, 1).append("xor %rax, %rax").append("\n");
        builder.repeat(INDENT, 1)
                .append("movl").append(" ")
                .append(src).append(", ")
                .append("%eax").append("\n");
    }

    private static void binary(
            StringBuilder builder,
            Map<Node, Register> registers,
            BinaryOperationNode node,
            String opcode) {
        var dest = registers.get(node);
        var left = registers.get(NodeSupport.predecessorSkipProj(node, BinaryOperationNode.LEFT));
        var right = registers.get(NodeSupport.predecessorSkipProj(node, BinaryOperationNode.RIGHT));

        switch (opcode) {
            case "addl", "subl", "imull" -> {
                builder.repeat(INDENT, 1)
                        .append("movl").append(" ")
                        .append(left).append(", ")
                        .append("%eax").append("\n");

                builder.repeat(INDENT, 1)
                        .append(opcode).append(" ")
                        .append(right).append(", ")
                        .append("%eax").append("\n");

                builder.repeat(INDENT, 1)
                        .append("movl").append(" ")
                        .append("%eax").append(", ")
                        .append(dest).append("\n");
            }
            case "idivl", "imodl" -> {
                builder.repeat(INDENT, 1).append("xorl %edx, %edx").append("\n");

                builder.repeat(INDENT, 1)
                        .append("movl").append(" ")
                        .append(left).append(", ")
                        .append("%eax").append("\n");

                builder.repeat(INDENT, 1)
                        .append("idivl").append(" ")
                        .append(right).append("\n");

                switch (opcode) {
                    case "idivl" -> {
                        builder.repeat(INDENT, 1)
                                .append("movl").append(" ")
                                .append("%eax").append(", ")
                                .append(dest).append("\n");
                    }
                    case "imodl" -> {
                        builder.repeat(INDENT, 1)
                                .append("movl").append(" ")
                                .append("%edx").append(", ")
                                .append(dest).append("\n");
                    }
                }
            }
        }

    }
}
