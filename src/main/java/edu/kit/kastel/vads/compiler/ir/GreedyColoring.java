package edu.kit.kastel.vads.compiler.ir;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import edu.kit.kastel.vads.compiler.ir.node.Node;

public class GreedyColoring {
    public static Map<Node, Integer> buildGreedyColoring(Map<Node, Set<Node>> interferenceGraph,
            List<Node> simplicalEliminationOrdering) {

        Map<Node, Integer> colorMap = new java.util.HashMap<>();
        for (Node node : simplicalEliminationOrdering) {
            colorMap.put(node, lowestColorNotUsedInNeighborhood(node, interferenceGraph, colorMap));
        }
        return colorMap;
    }

    private static int lowestColorNotUsedInNeighborhood(Node node,
            Map<Node, Set<Node>> interferenceGraph, Map<Node, Integer> colorMap) {

        Set<Integer> neighborColors =
                interferenceGraph.get(node).stream().map(colorMap::get).collect(Collectors.toSet());

        for (int i = 0;; i += 1) {
            if (!neighborColors.contains(i)) {
                return i;
            }
        }
    }
}
