package edu.kit.kastel.vads.compiler.lexer;

import edu.kit.kastel.vads.compiler.Position;
import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.lexer.Operator.OperatorType;
import edu.kit.kastel.vads.compiler.lexer.Separator.SeparatorType;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public class Lexer {
    private final String source;
    private int pos;
    private int lineStart;
    private int line;

    private Lexer(String source) {
        this.source = source;
    }

    public static Lexer forString(String source) {
        return new Lexer(source);
    }

    public Optional<Token> nextToken() {
        ErrorToken error = skipWhitespace();
        if (error != null) {
            return Optional.of(error);
        }

        // We've reached EOF after skipping whitespaces
        if (this.pos >= this.source.length()) {
            return Optional.empty();
        }

        return Optional.of(switch (peek()) {
            case '(' -> separator(SeparatorType.PAREN_OPEN);
            case ')' -> separator(SeparatorType.PAREN_CLOSE);
            case '{' -> separator(SeparatorType.BRACE_OPEN);
            case '}' -> separator(SeparatorType.BRACE_CLOSE);
            case ';' -> separator(SeparatorType.SEMICOLON);
            case '-' -> singleOrAssign(OperatorType.MINUS, OperatorType.ASSIGN_MINUS);
            case '+' -> singleOrAssign(OperatorType.PLUS, OperatorType.ASSIGN_PLUS);
            case '*' -> singleOrAssign(OperatorType.MUL, OperatorType.ASSIGN_MUL);
            case '/' -> singleOrAssign(OperatorType.DIV, OperatorType.ASSIGN_DIV);
            case '%' -> singleOrAssign(OperatorType.MOD, OperatorType.ASSIGN_MOD);
            case '=' -> new Operator(OperatorType.ASSIGN, buildSpan(1));
            default -> {
                if (isIdentifierChar(peek())) {
                    if (isNumeric(peek())) {
                        yield lexNumber();
                    }
                    yield lexIdentifierOrKeyword();
                }
                yield new ErrorToken(String.valueOf(peek()), buildSpan(1));
            }
        });
    }

    /// Skips whitespaces, newlines, and comments. Also has logic to correctly skip
    /// (possibly nested) multiline comments.
    ///
    /// @return An ErrorToken if there was an unterminated multiline comment
    private @Nullable ErrorToken skipWhitespace() {
        enum CommentType {
            SINGLE_LINE,
            MULTI_LINE
        }
        CommentType currentCommentType = null;
        int multiLineCommentDepth = 0;
        int commentStart = -1;
        while (hasMore(0)) {
            switch (peek()) {
                case ' ', '\t' -> this.pos++;
                case '\n', '\r' -> {
                    this.pos++;
                    this.lineStart = this.pos;
                    this.line++;
                    if (currentCommentType == CommentType.SINGLE_LINE) {
                        currentCommentType = null;
                    }
                }
                case '/' -> {
                    if (currentCommentType == CommentType.SINGLE_LINE) {
                        this.pos++;
                        continue;
                    }
                    if (hasMore(1)) {
                        if (peek(1) == '/' && currentCommentType == null) {
                            currentCommentType = CommentType.SINGLE_LINE;
                        } else if (peek(1) == '*') {
                            currentCommentType = CommentType.MULTI_LINE;
                            multiLineCommentDepth++;
                        } else {
                            return null;
                        }
                        commentStart = this.pos;
                        this.pos += 2;
                        continue;
                    }
                    // are we in a multi line comment of any depth?
                    if (multiLineCommentDepth > 0) {
                        this.pos++;
                        continue;
                    }
                    return null;
                }
                default -> {
                    if (currentCommentType == CommentType.MULTI_LINE) {
                        if (peek() == '*' && hasMore(1) && peek(1) == '/') {
                            this.pos += 2;
                            multiLineCommentDepth--;
                            currentCommentType = multiLineCommentDepth == 0 ? null : CommentType.MULTI_LINE;
                        } else {
                            this.pos++;
                        }
                        continue;
                    } else if (currentCommentType == CommentType.SINGLE_LINE) {
                        this.pos++;
                        continue;
                    }
                    return null;
                }
            }
        }
        if (!hasMore(0) && currentCommentType == CommentType.MULTI_LINE) {
            return new ErrorToken(this.source.substring(commentStart), buildSpan(0));
        }
        return null;
    }

    private Separator separator(SeparatorType parenOpen) {
        return new Separator(parenOpen, buildSpan(1));
    }

    private Token lexIdentifierOrKeyword() {
        // Walk past the identifier after which offset contains the length of it.
        int offset = 1;
        while (hasMore(offset) && isIdentifierChar(peek(offset))) {
            offset++;
        }

        // Extract the identifier from the source string
        String identifier = this.source.substring(this.pos, this.pos + offset);

        // Check if the identifier is a keyword
        if (KeywordType.keywords().containsKey(identifier)) {
            KeywordType type = KeywordType.keywords().get(identifier);
            return new Keyword(type, buildSpan(offset));
        }

        // Otherwise, return it as an identifier
        return new Identifier(identifier, buildSpan(offset));
    }

    private Token lexNumber() {
        if (isHexPrefix()) {
            int offset = 2;
            while (hasMore(offset) && isHex(peek(offset))) {
                offset++;
            }
            if (offset == 2) {
                // 0x without any further hex digits
                return new ErrorToken(this.source.substring(this.pos, this.pos + offset), buildSpan(2));
            }
            return new NumberLiteral(this.source.substring(this.pos, this.pos + offset), 16, buildSpan(offset));
        }

        // We set offset to `1` because this function is only called if the char at `0`
        // is a number.
        //
        // We then walk past the number literal after which `offset` contains the length
        // of the literal.
        int offset = 1;
        while (hasMore(offset) && isNumeric(peek(offset))) {
            offset++;
        }

        // Check for leading zero and reject
        if (peek() == '0' && offset > 1) {
            return new ErrorToken(this.source.substring(this.pos, this.pos + offset), buildSpan(offset));
        }

        return new NumberLiteral(this.source.substring(this.pos, this.pos + offset), 10, buildSpan(offset));
    }

    private boolean isHexPrefix() {
        return peek() == '0' && hasMore(1) && (peek(1) == 'x' || peek(1) == 'X');
    }

    /**
     * No emoji identifiers 😢
     */
    private boolean isIdentifierChar(char c) {
        return c == '_'
                || c >= 'a' && c <= 'z'
                || c >= 'A' && c <= 'Z'
                || c >= '0' && c <= '9';
    }

    private boolean isNumeric(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isHex(char c) {
        return isNumeric(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    /**
     * Check if the operator is followed by an equals sign (`a * 2` vs `a *= 2`)
     */
    private Token singleOrAssign(OperatorType single, OperatorType assign) {
        if (hasMore(1) && peek(1) == '=') {
            return new Operator(assign, buildSpan(2));
        }
        return new Operator(single, buildSpan(1));
    }

    private Span buildSpan(int proceed) {
        int start = this.pos;
        this.pos += proceed;
        Position.SimplePosition s = new Position.SimplePosition(this.line, start - this.lineStart);
        Position.SimplePosition e = new Position.SimplePosition(this.line, start - this.lineStart + proceed);
        return new Span.SimpleSpan(s, e);
    }

    private char peek() {
        return this.source.charAt(this.pos);
    }

    private boolean hasMore(int offset) {
        return this.pos + offset < this.source.length();
    }

    private char peek(int offset) {
        return this.source.charAt(this.pos + offset);
    }

}
