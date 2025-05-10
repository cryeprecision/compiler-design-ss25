package edu.kit.kastel.vads.compiler.backend.x86_64;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;

/// Infinite amount of registers, because they're all on the stack, lol.
public record StackRegister(int id) implements Register {
    @Override
    public String toString() {
        return String.format("-%d(%%rbp)", this.id() * 4);
    }
}
