package cl.cadcc.ramitos

import cats.data.NonEmptySeq
import cl.cadcc.ramitos.utils.Loc
import doobie.{Fragment, TypedFragment}
import doobie.syntax.all.*

import scala.language.implicitConversions

package object repository {
    case class EntityNotFoundException private[repository] (message: String)(using val source: Loc) extends RuntimeException(message)

    private def mkWhere(frs: Option[TypedFragment[Boolean]]*): Fragment = {
        val actualFrags =
            frs.collect {
                case Some(v) => v
            }
        if actualFrags.isEmpty then fr""
        else
            val cond = NonEmptySeq.fromSeqUnsafe(actualFrags).reduceLeft { (l, r) =>
                fr"$l AND $r"
            }
            // Interpolating through fr keeps the trailing space even for a single condition.
            fr"WHERE $cond"
    }

    enum SqlOrder {
        case ASCENDING
        case DESCENDING
    }
}
