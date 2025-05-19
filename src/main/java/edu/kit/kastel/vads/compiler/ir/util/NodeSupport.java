package edu.kit.kastel.vads.compiler.ir.util;

import java.util.Set;
import java.util.stream.Collectors;

import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.node.ProjNode;

public final class NodeSupport {
    private NodeSupport() {

    }

    public static Node predecessorSkipProj(Node node, int predIdx) {
        Node pred = node.predecessor(predIdx);
        if (pred instanceof ProjNode) {
            return pred.predecessor(ProjNode.IN);
        }
        return pred;
    }

    public static Set<Node> successorsSkipProj(IrGraph graph, Node node) {
        return graph.successors(node).stream()
                .map(successor -> switch (successor) {
                    case ProjNode _ -> {
                        yield graph.successors(successor);
                    }
                    default -> {
                        yield Set.of(successor);
                    }
                })
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }
}
