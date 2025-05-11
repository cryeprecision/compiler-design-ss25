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
            // FIXME: Very bad, allocates many objects
            List<String> lines = source.lines().toList();
            StringBuilder builder = new StringBuilder();

            for (int i = start().line(); i <= end().line(); i++) {
                String line = lines.get(i);
                builder.append(line).append("\n");
            }

            return builder.toString();
        }
    }
}
