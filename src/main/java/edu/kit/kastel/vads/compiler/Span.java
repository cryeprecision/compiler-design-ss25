package edu.kit.kastel.vads.compiler;

import java.util.List;

public sealed interface Span {
    Position start();

    Position end();

    Span merge(Span later);

    String fromSource(String source);

    record SimpleSpan(Position start, Position end) implements Span {
        @Override
        public Span merge(Span later) {
            return new SimpleSpan(start(), later.end());
        }

        @Override
        public String toString() {
            return "[" + start() + "|" + end() + "]";
        }

        @Override
        public String fromSource(String source) {
            if (start.line() != end.line()) {
                return toString();
            }
            List<String> lines = source.lines().toList();
            return lines.get(start.line()).substring(start.column(), end.column()).trim();
        }
    }
}
