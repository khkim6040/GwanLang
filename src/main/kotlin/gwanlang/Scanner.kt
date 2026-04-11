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
        // 다음 사이클에서 확장
        advance()
    }

    private fun isAtEnd(): Boolean = current >= source.length

    private fun advance(): Char = source[current++]
}
