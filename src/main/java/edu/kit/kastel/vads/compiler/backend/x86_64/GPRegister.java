package edu.kit.kastel.vads.compiler.backend.x86_64;

import java.util.List;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;

/// General Purpose Register
public class GPRegister {
    static public Register forColor(int color) {
        List<x86Register> registers = List.of(x86Register.values());
        if (color < registers.size()) {
            return registers.get(color);
        }
        return new SpillRegister(color - registers.size());
    }

    public enum x86Register implements Register {
        // EAX("eax"), // Reserved for idiv instruction
        EBX("ebx"),
        ECX("ecx"),
        // EDX("edx"), // Reserved for idiv instruction
        ESI("esi"),
        EDI("edi"),
        R8D("r8d"),
        R9D("r9d"),
        R10D("r10d"),
        // R11D("r11d"), // Reserved for handling spilled registers
        R12D("r12d"),
        R13D("r13d"),
        R14D("r14d"),
        R15D("r15d");

        private final String name;

        x86Register(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "%%%s".formatted(this.name);
        }

        @Override
        public boolean isSpillRegister() {
            return false;
        }
    }

    public record SpillRegister(int offset) implements Register {
        @Override
        public boolean isSpillRegister() {
            return true;
        }

        @Override
        public final String toString() {
            return "-%d(%%rbp)".formatted((this.offset + 1) * 4);
        }
    }
}
