package edu.kit.kastel.vads.compiler.ir;

import edu.kit.kastel.vads.compiler.ir.node.Node;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BasicNodeOrder {
  private static void buildBasicNodeOrderRecursive(Node node, Set<Node> visited, List<Node> order) {
    for (Node predecessor : node.predecessors()) {
      if (visited.add(predecessor)) {
        buildBasicNodeOrderRecursive(predecessor, visited, order);
      }
    }
    order.add(node);
  }

  public static List<Node> buildBasicNodeOrder(IrGraph graph) {
    Set<Node> visited = new HashSet<>();
    List<Node> order = new ArrayList<>();

    buildBasicNodeOrderRecursive(graph.endBlock(), visited, order);
    return order;
  }
}
