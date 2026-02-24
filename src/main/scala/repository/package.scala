package cl.cadcc.ramitos

import cl.cadcc.ramitos.utils.Loc

package object repository {
    case class EntityNotFoundException private[repository] (message: String)(using val source: Loc) extends RuntimeException(message)
}
