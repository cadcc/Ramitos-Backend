package cl.cadcc.ramitos.utils

import scala.quoted.{Expr, Quotes}

final case class Range(start: Int, end: Int)
final case class Loc(ownerName: String, lines: Range, columns: Range)

object Loc {

    inline given instance: Loc = ${ origin }

    private def getClassOrMethodOwner(using quotes: Quotes)(sym: quotes.reflect.Symbol): quotes.reflect.Symbol =
        if sym.isClassDef || sym.isDefDef || sym.isPackageDef then sym
        else getClassOrMethodOwner(sym.owner)

    def origin(using quotes: Quotes): Expr[Loc] =
        val owner = getClassOrMethodOwner(quotes.reflect.Symbol.spliceOwner)
        val pos = quotes.reflect.Position.ofMacroExpansion

        val ownerName = Expr(owner.fullName)
        val lineStart = Expr(pos.start)
        val lineEnd   = Expr(pos.end)
        val colStart  = Expr(pos.startColumn)
        val colEnd    = Expr(pos.endColumn)
        '{ Loc($ownerName, Range($lineEnd, $lineStart), Range($colStart, $colEnd)) }
}
