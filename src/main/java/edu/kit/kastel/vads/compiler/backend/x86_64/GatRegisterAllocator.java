package edu.kit.kastel.vads.compiler.backend.x86_64;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

public class GatRegisterAllocator implements RegisterAllocator {
  @Override
  public Map<Node, Register> allocateRegisters(IrGraph graph, String source) {
    List<Node> basicOrder = BasicNodeOrder
        .buildBasicNodeOrder(graph);
    Map<Node, Set<Node>> interferenceGraph = InterferenceGraph
        .buildInterferenceGraph(graph);
    List<Node> simplicalEliminationOrder = SimplicalEliminationOrdering
        .buildSimplicalEliminationOrdering(interferenceGraph);
    Map<Node, Integer> interferenceGraphColoring = GreedyColoring
        .buildGreedyColoring(interferenceGraph, simplicalEliminationOrder);
    Map<Node, Register> registerAllocation = allocateRegistersInner(interferenceGraphColoring);

    // Log everything for debugging
    System.out.println("[GatRegisterAllocator] Basic node order:");
    for (int i = basicOrder.size() - 1; i >= 0; i -= 1) {
      Node node = basicOrder.get(i);

      int reversedIndex = basicOrder.size() - i - 1;
      Set<Node> interferenceSet = interferenceGraph.get(node) == null
          ? Set.of()
          : interferenceGraph.get(node);

      System.out.println(" - %20s [idx: %2d, col: %2d, reg: %s]: %20s '%s'".formatted(
          node,
          reversedIndex,
          interferenceGraphColoring.get(node),
          registerAllocation.get(node),
          interferenceSet,
          node.sourceSpan(source)));
    }

    return registerAllocation;
  }

  private Map<Node, Register> allocateRegistersInner(
      Map<Node, Integer> interferenceGraphColoring) {
    Map<Node, Register> result = new HashMap<>();
    interferenceGraphColoring.entrySet().stream()
        .filter(entry -> needsRegister(entry.getKey()))
        .forEach(entry -> result.put(entry.getKey(), GPRegister.forColor(entry.getValue())));
    return result;
  }

  /// These nodes do not produce a result
  private static boolean needsRegister(Node node) {
    return !(node instanceof ProjNode || node instanceof StartNode ||
        node instanceof Block || node instanceof ReturnNode);
  }
}
