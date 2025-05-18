package edu.kit.kastel.vads.compiler.ir;

import edu.kit.kastel.vads.compiler.ir.node.BinaryOperationNode;
import edu.kit.kastel.vads.compiler.ir.node.ConstIntNode;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.node.ReturnNode;
import edu.kit.kastel.vads.compiler.ir.util.NodeSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InterferenceGraph {
  public static Map<Node, Set<Node>> buildInterferenceGraph(IrGraph graph) {
    List<Node> basicNodeOrderRev =
        BasicNodeOrder.buildBasicNodeOrder(graph).reversed();

    Map<Node, Set<Node>> interferenceGraph = new HashMap<>();
    Set<Node> liveNodes = new HashSet<>();

    for (Node node : basicNodeOrderRev) {
      System.out.println("[InterferenceGraph] Visiting node: " + node + " " +
                         node.hashCode());

      // Determine which other nodes are live at this node
      switch (node) {
        // return x
      case ReturnNode retNode -> {
        Node rValue =
            NodeSupport.predecessorSkipProj(retNode, ReturnNode.RESULT);

        // L3: The returned value is live at the return node
        liveNodes.add(rValue);
      }

        // x <- constant
      case ConstIntNode constIntNode -> {
        // L4: Same as for L2, except we don't have operands
        liveNodes.remove(constIntNode);
      }

        // x <- lhs [op] rhs
      case BinaryOperationNode binaryOperationNode -> {
        Node lhs = NodeSupport.predecessorSkipProj(binaryOperationNode,
                                                   BinaryOperationNode.LEFT);
        Node rhs = NodeSupport.predecessorSkipProj(binaryOperationNode,
                                                   BinaryOperationNode.RIGHT);

        // L1: The operands of the binary operation are live at the binary
        // operation node
        liveNodes.add(lhs);
        liveNodes.add(rhs);

        // L2: If a value is live at the next line (previsouly processed) it is
        // live at this line _unless_ it is assigned to
        liveNodes.remove(binaryOperationNode);
      }

        // No rules for other kinds of nodes
      default -> {
        /* no-op */
      }
      } // switch (node)

      // If a node v is live at a node u, then u and v interfere
      // so we add edges (u, v) and (v, u)
      List<Node> liveNodesList = new ArrayList<>(liveNodes);
      for (int i = 0; i < liveNodesList.size(); i += 1) {
        Node u = liveNodesList.get(i);
        for (int j = 0; j < liveNodesList.size(); j += 1) {
          Node v = liveNodesList.get(j);

          if (u != v) {
            Set<Node> uAdjacent =
                interferenceGraph.computeIfAbsent(u, (_) -> new HashSet<>());
            Set<Node> vAdjacent =
                interferenceGraph.computeIfAbsent(v, (_) -> new HashSet<>());

            uAdjacent.add(v);
            vAdjacent.add(u);
          }
        } // for (j < i)
      } // for (i < liveNodesList.size())
    } // for (node : basicNodeOrderRev)

    return interferenceGraph;
  }
}
