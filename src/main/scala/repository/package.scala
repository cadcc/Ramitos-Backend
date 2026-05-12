package cl.cadcc.ramitos

import cl.cadcc.ramitos.utils.Loc
import doobie.{Fragment, TypedFragment}
import doobie.syntax.all.*

import scala.language.implicitConversions

package object repository {
    case class EntityNotFoundException private[repository] (message: String)(using val source: Loc) extends RuntimeException(message)

    private def mkWhere(frs: Option[TypedFragment[Boolean]]*): Fragment =
        frs.collect {
            case Some(v) => v
        }.foldLeft(fr"WHERE ")(_ ++ _ ++ fr" ")

    enum SqlOrder {
        case ASCENDING
        case DESCENDING
    }
}
