package cl.cadcc.ramitos.utils

import cats.mtl.Ask
import cats.syntax.all._
import cats.MonadThrow
import cats.Applicative

class AskWithUnderlingOption[F[_], E](using mt: MonadThrow[F])(val message: String, val underling: Ask[F, Option[E]]) extends Ask[F, E] {
    def applicative: Applicative[F] = implicitly

    def ask[E2 >: E]: F[E2] =
        underling.ask.flatMap {
            case None => mt.raiseError(new IllegalAccessException(message))
            case Some(v) => v.pure
        }
}
