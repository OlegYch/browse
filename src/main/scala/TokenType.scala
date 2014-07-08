package sxr

sealed abstract class TokenType(val literal: Boolean = false)

case object IntToken extends TokenType(true)

case object LongToken extends TokenType(true)

case object FloatToken extends TokenType(true)

case object DoubleToken extends TokenType(true)

case object CharToken extends TokenType(true)

case object StringToken extends TokenType(true)

case object SymbolToken extends TokenType(true)

case object CommentToken extends TokenType

case object DelimiterToken extends TokenType

case object KeywordToken extends TokenType

case object UscoreToken extends TokenType

case object IdentifierToken extends TokenType

object TokenType {
  def apply(code: Int): Option[TokenType] = {
    import scala.tools.nsc.ast.parser.Tokens._
    val m: PartialFunction[Int, TokenType] = {
      case CHARLIT => CharToken
      case INTLIT => IntToken
      case LONGLIT => LongToken
      case FLOATLIT => FloatToken
      case DOUBLELIT => DoubleToken
      case STRINGLIT => StringToken
      case SYMBOLLIT => SymbolToken
      case COMMENT => CommentToken
      case USCORE => UscoreToken
      case IDENTIFIER | BACKQUOTED_IDENT => IdentifierToken
      case LPAREN | RPAREN | LBRACKET | RBRACKET | LBRACE | RBRACE => DelimiterToken
      case _ if code >= NEW && code <= RETURN => KeywordToken
    }
    m.lift(code)
  }
}
