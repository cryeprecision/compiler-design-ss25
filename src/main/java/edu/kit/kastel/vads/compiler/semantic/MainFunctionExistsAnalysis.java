package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor;
import edu.kit.kastel.vads.compiler.parser.visitor.Unit;

public class MainFunctionExistsAnalysis implements NoOpVisitor<Namespace<Void>> {

    @Override
    public Unit visit(ProgramTree programTree, Namespace<Void> data) {
        long mainFns = programTree.topLevelTrees().stream()
                .filter(fnTree -> fnTree.name().name().asString().equals("main"))
                .count();

        if (mainFns < 1) {
            throw new SemanticException("missing main function");
        }
        // TODO: This is currently never hit, because the parser panics when there are
        // multiple top level definitions
        if (mainFns > 1) {
            throw new SemanticException("found multiple main functions");
        }

        return NoOpVisitor.super.visit(programTree, data);
    }
}
