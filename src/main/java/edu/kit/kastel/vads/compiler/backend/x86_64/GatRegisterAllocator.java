package edu.kit.kastel.vads.compiler.backend.x86_64;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;
import edu.kit.kastel.vads.compiler.backend.regalloc.RegisterAllocator;
import edu.kit.kastel.vads.compiler.ir.BasicNodeOrder;
import edu.kit.kastel.vads.compiler.ir.GreedyColoring;
import edu.kit.kastel.vads.compiler.ir.InterferenceGraph;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.SimplicalEliminationOrdering;
import edu.kit.kastel.vads.compiler.ir.node.Block;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.node.ProjNode;
import edu.kit.kastel.vads.compiler.ir.node.ReturnNode;
import edu.kit.kastel.vads.compiler.ir.node.StartNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GatRegisterAllocator implements RegisterAllocator {
  private int lastTemporaryId;
  private final Map<Node, GPRegister.Temporary> tempRegisters = new HashMap<>();

  @Override
  public Map<Node, Register> allocateRegisters(IrGraph graph, String source) {
    List<Node> basicOrder = BasicNodeOrder.buildBasicNodeOrder(graph);
    Map<Node, Set<Node>> interferenceGraph = InterferenceGraph.buildInterferenceGraph(graph);
    List<Node> simplicalEliminationOrder =
        SimplicalEliminationOrdering.buildSimplicalEliminationOrdering(interferenceGraph);
    Map<Node, Integer> interferenceGraphColoring =
        GreedyColoring.buildGreedyColoring(interferenceGraph, simplicalEliminationOrder);

    // Log everything for debugging
    System.out.println("[GatRegisterAllocator] Basic node order:");
    for (int i = basicOrder.size() - 1; i >= 0; i -= 1) {
      Node node = basicOrder.get(i);

      int reversedIndex = basicOrder.size() - i - 1;
      Set<Node> interferenceSet =
          interferenceGraph.get(node) == null ? Set.of() : interferenceGraph.get(node);

      System.out
          .println(" - " + node + " [" + reversedIndex + "::" + interferenceGraphColoring.get(node)
              + "] \"" + node.sourceSpan(source) + "\": " + interferenceSet);
    }
    System.out.println("[GatRegisterAllocator] Simplical elimination order:");
    for (Node node : simplicalEliminationOrder) {
      System.out.println(" - " + node);
    }

    Set<Node> visited = new HashSet<>();
    visited.add(graph.endBlock());
    allocTempsRecursive(graph.endBlock(), visited);

    return Map.copyOf(this.tempRegisters);
  }

  private void allocTempsRecursive(Node node, Set<Node> visited) {
    for (Node predecessor : node.predecessors()) {
      if (visited.add(predecessor))
        allocTempsRecursive(predecessor, visited);
    }

    if (needsRegister(node)) {
      this.tempRegisters.put(node, new GPRegister.Temporary(this.lastTemporaryId++));
    }
  }

  /// These nodes do not produce a result
  private static boolean needsRegister(Node node) {
    return !(node instanceof ProjNode || node instanceof StartNode || node instanceof Block
        || node instanceof ReturnNode);
  }
}
