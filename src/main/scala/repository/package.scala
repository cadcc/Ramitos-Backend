package cl.cadcc.ramitos

package object repository {
    case class EntityNotFoundException(message: String) extends Exception
}
