package cl.cadcc.ramitos.compiletime

import scala.quoted.Quotes
import scala.quoted.Expr

private[compiletime] object PropertyMacros {

    inline def keyPathMacro = ${ getKeyPath }

    private def getKeyPath(using Quotes): Expr[Option[String]] =
        val path = System.getProperty("ramitos.keyPath", "")
        val pathOpt = if path.isBlank then None else Some(path)
        Expr(pathOpt)
}
