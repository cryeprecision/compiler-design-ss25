package edu.kit.kastel.vads.compiler.backend.x86_64;

import java.util.Set;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;
import edu.kit.kastel.vads.compiler.ir.node.DivNode;
import edu.kit.kastel.vads.compiler.ir.node.ModNode;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.node.StartNode;

/// General Purpose Register
public class GPRegister {
    public record Temporary(int id) implements Register {
        @Override
        public String toString() {
            return String.format("t%d", this.id());
        }

        public boolean equals(Object obj) {
            if (obj instanceof Temporary other) {
                return this.id() == other.id();
            }
            return false;
        }
    }

    public enum x86Register implements Register {
        RSP("RSP"),
        RBP("RBP"),
        EAX("EAX"),
        EBX("EBX"),
        ECX("ECX"),
        EDX("EDX"),
        ESI("ESI"),
        EDI("EDI"),
        R8D("R8D"),
        R9D("R9D"),
        R10D("R10D"),
        R11D("R11D"),
        R12D("R12D"),
        R13D("R13D"),
        R14D("R14D"),
        R15D("R15D"),
        ;

        private final String name;

        x86Register(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static Set<Register> sideEffectRegs(Node node) {
            switch (node) {
                case DivNode _ -> {
                    return Set.of(x86Register.EDX);
                }
                case ModNode _ -> {
                    return Set.of(x86Register.EDX);
                }
                case StartNode _ -> {
                    // TODO: ???
                    return Set.of(x86Register.RSP, x86Register.RBP);
                }
                default -> {
                    return Set.of();
                }
            }
        }
    }
}
