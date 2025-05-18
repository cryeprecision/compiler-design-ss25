package edu.kit.kastel.vads.compiler.ir;

import edu.kit.kastel.vads.compiler.ir.node.AddNode;
import edu.kit.kastel.vads.compiler.ir.node.Block;
import edu.kit.kastel.vads.compiler.ir.node.ConstIntNode;
import edu.kit.kastel.vads.compiler.ir.node.DivNode;
import edu.kit.kastel.vads.compiler.ir.node.ModNode;
import edu.kit.kastel.vads.compiler.ir.node.MulNode;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.node.Phi;
import edu.kit.kastel.vads.compiler.ir.node.ProjNode;
import edu.kit.kastel.vads.compiler.ir.node.ReturnNode;
import edu.kit.kastel.vads.compiler.ir.node.StartNode;
import edu.kit.kastel.vads.compiler.ir.node.SubNode;
import edu.kit.kastel.vads.compiler.ir.optimize.Optimizer;
import edu.kit.kastel.vads.compiler.parser.symbol.Name;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class GraphConstructor {

    private final Optimizer optimizer;
    private final IrGraph graph;
    private final Map<Name, Map<Block, Node>> currentDef = new HashMap<>();
    private final Map<Block, Map<Name, Phi>> incompletePhis = new HashMap<>();
    private final Map<Block, Node> currentSideEffect = new HashMap<>();
    private final Map<Block, Phi> incompleteSideEffectPhis = new HashMap<>();
    private final Set<Block> sealedBlocks = new HashSet<>();
    private Block currentBlock;

    public GraphConstructor(Optimizer optimizer, String name) {
        this.optimizer = optimizer;
        this.graph = new IrGraph(name);
        this.currentBlock = this.graph.startBlock();
        // the start block never gets any more predecessors
        sealBlock(this.currentBlock);
    }

    public Node newStart() {
        assert currentBlock() == this.graph.startBlock() : "start must be in start block";
        return new StartNode(currentBlock());
    }

    public Node newAdd(Node left, Node right) {
        return this.optimizer.transform(new AddNode(currentBlock(), left, right));
    }

    public Node newSub(Node left, Node right) {
        return this.optimizer.transform(new SubNode(currentBlock(), left, right));
    }

    public Node newMul(Node left, Node right) {
        return this.optimizer.transform(new MulNode(currentBlock(), left, right));
    }

    public Node newDiv(Node left, Node right) {
        return this.optimizer
                .transform(new DivNode(currentBlock(), left, right, readCurrentSideEffect()));
    }

    public Node newMod(Node left, Node right) {
        return this.optimizer
                .transform(new ModNode(currentBlock(), left, right, readCurrentSideEffect()));
    }

    public Node newReturn(Node result) {
        return new ReturnNode(currentBlock(), readCurrentSideEffect(), result);
    }

    public Node newConstInt(int value) {
        // always move const into start block, this allows better deduplication
        // and resultingly in better value numbering
        return this.optimizer.transform(new ConstIntNode(this.graph.startBlock(), value));
    }

    public Node newSideEffectProj(Node node) {
        return new ProjNode(currentBlock(), node, ProjNode.SimpleProjectionInfo.SIDE_EFFECT);
    }

    public Node newResultProj(Node node) {
        return new ProjNode(currentBlock(), node, ProjNode.SimpleProjectionInfo.RESULT);
    }

    public Block currentBlock() {
        return this.currentBlock;
    }

    public Phi newPhi() {
        // don't transform phi directly, it is not ready yet
        return new Phi(currentBlock());
    }

    public IrGraph graph() {
        return this.graph;
    }

    /// @see {@link edu.kit.kastel.vads.compiler.ir.GraphConstructor#readVariable}
    void writeVariable(Name variable, Block block, Node value) {
        this.currentDef.computeIfAbsent(variable, _ -> new HashMap<>()).put(block, value);
    }

    /// Implements local value numbering (together with `writeVariable`).
    ///
    /// @see {@link edu.kit.kastel.vads.compiler.ir.GraphConstructor#writeVariable}
    ///
    /// ## Info
    ///
    /// - When the local value numbering for a block is finished, we call that block _filled_.
    /// - Successors may only be added to _filled_ blocks.
    Node readVariable(Name variable, Block block) {
        // Local value numbering
        Node node = this.currentDef.getOrDefault(variable, Map.of()).get(block);
        if (node != null) {
            return node;
        }

        // Global value numbering
        return readVariableRecursive(variable, block);
    }

    /// Implements global value numbering (together with `addPhiOperands`)
    ///
    /// @see {@link edu.kit.kastel.vads.compiler.ir.GraphConstructor#addPhiOperands}
    private Node readVariableRecursive(Name variable, Block block) {
        Node val;
        if (!this.sealedBlocks.contains(block)) {
            // Handle incomplete CFG
            // This block is not _sealed_ yet, so more predecessors might be added
            val = newPhi();
            this.incompletePhis.computeIfAbsent(block, _ -> new HashMap<>()).put(variable,
                    (Phi) val);
        } else if (block.predecessors().size() == 1) {
            // There's only one predecessor
            // => No Phi needed
            val = readVariable(variable, block.predecessors().getFirst().block());
        } else {
            // Break potential cycles with operandless Phi
            val = newPhi();
            writeVariable(variable, block, val);
            val = addPhiOperands(variable, (Phi) val);
        }

        writeVariable(variable, block, val);
        return val;
    }

    /// @see {@link edu.kit.kastel.vads.compiler.ir.GraphConstructor#readVariableRecursive}
    Node addPhiOperands(Name variable, Phi phi) {
        // Determine operands from predecessors
        for (Node pred : phi.block().predecessors()) {
            phi.appendOperand(readVariable(variable, pred.block()));
        }
        return tryRemoveTrivialPhi(phi);
    }

    Node tryRemoveTrivialPhi(Phi phi) {
        // TODO: "Simple and Efficient SSA Construction" Algorithm 3
        return phi;
    }

    /// A basic block is _sealed_, if no further predecessors will be added to it.
    ///
    /// Sealing a block is an explicit action during IR construction.
    ///
    /// - As only _filled_ blocks may have successors, _predecessors_ are always _filled_.
    /// - A _sealed_ block is **not** necessarily _filled_.
    /// - A _filled_ block can _provide_ variable definitions _for its successors_.
    /// - A _sealed_ block may _look up_ variable definitions _in its predecessors_, as all predecessors are known.
    void sealBlock(Block block) {
        for (Map.Entry<Name, Phi> entry : this.incompletePhis.getOrDefault(block, Map.of())
                .entrySet()) {
            addPhiOperands(entry.getKey(), entry.getValue());
        }
        this.sealedBlocks.add(block);
    }

    public void writeCurrentSideEffect(Node node) {
        writeSideEffect(currentBlock(), node);
    }

    private void writeSideEffect(Block block, Node node) {
        this.currentSideEffect.put(block, node);
    }

    public Node readCurrentSideEffect() {
        return readSideEffect(currentBlock());
    }

    private Node readSideEffect(Block block) {
        Node node = this.currentSideEffect.get(block);
        if (node != null) {
            return node;
        }
        return readSideEffectRecursive(block);
    }

    private Node readSideEffectRecursive(Block block) {
        Node val;
        if (!this.sealedBlocks.contains(block)) {
            val = newPhi();
            Phi old = this.incompleteSideEffectPhis.put(block, (Phi) val);
            assert old == null : "double readSideEffectRecursive for " + block;
        } else if (block.predecessors().size() == 1) {
            val = readSideEffect(block.predecessors().getFirst().block());
        } else {
            val = newPhi();
            writeSideEffect(block, val);
            val = addPhiOperands((Phi) val);
        }
        writeSideEffect(block, val);
        return val;
    }

    Node addPhiOperands(Phi phi) {
        for (Node pred : phi.block().predecessors()) {
            phi.appendOperand(readSideEffect(pred.block()));
        }
        return tryRemoveTrivialPhi(phi);
    }

}
