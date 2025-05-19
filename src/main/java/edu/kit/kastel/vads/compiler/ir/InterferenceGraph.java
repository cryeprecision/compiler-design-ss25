package edu.kit.kastel.vads.compiler.ir;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import edu.kit.kastel.vads.compiler.ir.node.BinaryOperationNode;
import edu.kit.kastel.vads.compiler.ir.node.Block;
import edu.kit.kastel.vads.compiler.ir.node.ConstIntNode;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.node.ReturnNode;
import edu.kit.kastel.vads.compiler.ir.util.NodeSupport;

public class InterferenceGraph {
  private final static boolean LOG_LIVE_IN_SETS = true;

  public static Map<Node, Set<Node>> buildInterferenceGraph(IrGraph graph) {

    Map<Node, Set<Node>> liveIn = new HashMap<>();
    Set<Node> liveInCurrent = new HashSet<>();

    List<Node> basicNodeOrderRev = BasicNodeOrder.buildBasicNodeOrder(graph).reversed();
    for (Node node : basicNodeOrderRev) {
      // Determine which other nodes are live at this node
      switch (node) {
        // return x
        case ReturnNode retNode -> {
          Node rValue = NodeSupport.predecessorSkipProj(retNode, ReturnNode.RESULT);

          // L3: The returned value is live at the return node
          liveInCurrent.add(rValue);
          liveIn.computeIfAbsent(retNode, (_) -> new HashSet<>()).addAll(liveInCurrent);
        }

        // x <- constant
        case ConstIntNode constIntNode -> {
          // L4: Same as for L2, except we don't have operands
          liveInCurrent.remove(constIntNode);
          liveIn.computeIfAbsent(constIntNode, (_) -> new HashSet<>()).addAll(liveInCurrent);
        }

        // x <- lhs [op] rhs
        case BinaryOperationNode binOpNode -> {
          Node lhs = NodeSupport.predecessorSkipProj(binOpNode, BinaryOperationNode.LEFT);
          Node rhs = NodeSupport.predecessorSkipProj(binOpNode, BinaryOperationNode.RIGHT);

          // L1: The operands of the binary operation are live at the binary operation
          // node
          liveInCurrent.add(lhs);
          liveInCurrent.add(rhs);
          // L2: If a value is live at the next line (previsouly processed) it is live at
          // this line _unless_ it is assigned to
          liveInCurrent.remove(binOpNode);

          liveIn.computeIfAbsent(binOpNode, (_) -> new HashSet<>()).addAll(liveInCurrent);
        }

        // Return register is live after the last instruction
        case Block _ -> {
          // TODO: Return register is live after the last instruction
        }

        // No rules for other kinds of nodes
        default -> {
          /* no-op */
        }
      } // switch (node)
    } // for (node : basicNodeOrderRev)

    if (LOG_LIVE_IN_SETS) {
      System.out.println("[InterferenceGraph] Live-in sets:");
      for (int i = 0; i < basicNodeOrderRev.size(); i += 1) {
        Node node = basicNodeOrderRev.get(i);
        Set<Node> liveAtNode = liveIn.get(node);
        System.out.println(" - " + node + " [" + i + "]: " + liveAtNode);
      }
    }

    // Compute the interference graph from the live-in information
    Map<Node, Set<Node>> interferenceGraph = new HashMap<>();
    for (Node node : basicNodeOrderRev) {
      interferenceGraph.put(node, new HashSet<>());
    }

    for (Entry<Node, Set<Node>> entry : liveIn.entrySet()) {
      Node u = entry.getKey();

      switch (u) {
        case BinaryOperationNode _,ConstIntNode _ -> {
          Set<Node> liveInAtSuccessors = NodeSupport.successorsSkipProj(graph, u)
              .stream()
              .flatMap((v) -> liveIn.getOrDefault(v, Set.of()).stream())
              .filter((v) -> !v.equals(u))
              .collect(Collectors.toSet());

          for (Node v : liveInAtSuccessors) {
            interferenceGraph.get(u).add(v);
            interferenceGraph.get(v).add(u);
          }
        }
        default -> {
          /* no-op */
        }
      }
    }
    return interferenceGraph;
  }
}
