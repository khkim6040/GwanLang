package gwanlang

class Scanner(private val source: String) {
    private val tokens = mutableListOf<Token>()
    private var start = 0
    private var current = 0
    private var line = 1

    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            start = current
            scanToken()
        }
        tokens.add(Token(TokenType.EOF, "", null, line))
        return tokens
    }

    private fun scanToken() {
        val c = advance()
        when {
            c == '(' -> addToken(TokenType.LEFT_PAREN)
            c == ')' -> addToken(TokenType.RIGHT_PAREN)
            c == '{' -> addToken(TokenType.LEFT_BRACE)
            c == '}' -> addToken(TokenType.RIGHT_BRACE)
            c == ',' -> addToken(TokenType.COMMA)
            c == '.' -> addToken(TokenType.DOT)
            c == '-' -> addToken(TokenType.MINUS)
            c == '+' -> addToken(TokenType.PLUS)
            c == ';' -> addToken(TokenType.SEMICOLON)
            c == '*' -> addToken(TokenType.STAR)
            c == '!' -> addToken(if (match('=')) TokenType.BANG_EQUAL else TokenType.BANG)
            c == '=' -> addToken(if (match('=')) TokenType.EQUAL_EQUAL else TokenType.EQUAL)
            c == '>' -> addToken(if (match('=')) TokenType.GREATER_EQUAL else TokenType.GREATER)
            c == '<' -> addToken(if (match('=')) TokenType.LESS_EQUAL else TokenType.LESS)
            c == '/' -> slashOrComment()
            c == ' ' || c == '\r' || c == '\t' -> { /* 공백 무시 */ }
            c == '\n' -> line++
            c == '"' -> string()
            isDigit(c) -> number()
            isAlpha(c) -> identifier()
            // 에러는 Cycle 11에서 처리
        }
    }

    private fun slashOrComment() {
        if (peek() == '/') {
            // 단일 라인 주석: 개행 직전까지 소비
            while (peek() != '\n' && !isAtEnd()) advance()
        } else {
            addToken(TokenType.SLASH)
        }
    }

    private fun string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++
            advance()
        }
        if (isAtEnd()) {
            GwanLang.error(line, "Unterminated string.")
            return
        }
        advance() // 닫는 " 소비
        val value = source.substring(start + 1, current - 1)
        addToken(TokenType.STRING, value)
    }

    private fun number() {
        while (isDigit(peek())) advance()
        if (peek() == '.' && isDigit(peekNext())) {
            advance() // '.' 소비
            while (isDigit(peek())) advance()
        }
        val value = source.substring(start, current).toDouble()
        addToken(TokenType.NUMBER, value)
    }

    private fun identifier() {
        while (isAlphaNumeric(peek())) advance()
        addToken(TokenType.IDENTIFIER)
    }

    private fun isDigit(c: Char): Boolean = c in '0'..'9'

    private fun isAlpha(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c == '_'

    private fun isAlphaNumeric(c: Char): Boolean = isAlpha(c) || isDigit(c)

    private fun isAtEnd(): Boolean = current >= source.length

    private fun advance(): Char = source[current++]

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false
        current++
        return true
    }

    private fun peek(): Char = if (isAtEnd()) '\u0000' else source[current]

    private fun peekNext(): Char =
        if (current + 1 >= source.length) '\u0000' else source[current + 1]

    private fun addToken(type: TokenType, literal: Any? = null) {
        val lexeme = source.substring(start, current)
        tokens.add(Token(type, lexeme, literal, line))
    }
}
