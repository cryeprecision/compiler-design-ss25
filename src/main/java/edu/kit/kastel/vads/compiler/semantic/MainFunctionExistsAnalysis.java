package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor;
import edu.kit.kastel.vads.compiler.parser.visitor.Unit;

public class MainFunctionExistsAnalysis implements NoOpVisitor<Namespace<Void>> {

    @Override
    public Unit visit(ProgramTree programTree, Namespace<Void> data) {
        programTree.topLevelTrees().stream()
                .filter(fnTree -> fnTree.name().name().asString() == "main")
                .findAny().orElseThrow(() -> new SemanticException("missing main function"));
        return NoOpVisitor.super.visit(programTree, data);
    }
}
