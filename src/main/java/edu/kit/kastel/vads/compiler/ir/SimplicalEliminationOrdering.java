package edu.kit.kastel.vads.compiler.ir;

import edu.kit.kastel.vads.compiler.ir.node.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SimplicalEliminationOrdering {
  public static List<Node> buildSimplicalEliminationOrdering(
      // G = (V, E)
      Map<Node, Set<Node>> interferenceGraph) {
    // TODO: Should we exclude specific nodes like ProjNode here?

    // This will contain the nodes in their simplical elimination ordering
    List<Node> order = new ArrayList<>();
    for (int i = 0; i < interferenceGraph.size(); i += 1) {
      order.add(null);
    }

    // for each v: weight[v] <- 0;
    Map<Node, Integer> wt = new HashMap<>();
    for (Node v : interferenceGraph.keySet()) {
      wt.put(v, 0);
    }

    // W <- V
    Set<Node> W = new HashSet<>(interferenceGraph.keySet());

    for (int i = 0; i < interferenceGraph.size(); i++) {
      // Let v be the node with the maximum weight in W
      Node v = wt.entrySet()
                   .stream()
                   .filter(entry -> W.contains(entry.getKey()))
                   .max(Map.Entry.comparingByValue())
                   .orElseThrow()
                   .getKey();

      // Set v_i <- v
      order.set(i, v);

      // For all u ∈ W ∩ N(v) set weight[u] <- weight[u] + 1
      for (Node u :
           interferenceGraph.get(v).stream().filter(W::contains).toList()) {
        wt.computeIfPresent(u, (_, w) -> w + 1);
      }

      // W <- W - {v}
      W.remove(v);
    }

    return order;
  }
}
