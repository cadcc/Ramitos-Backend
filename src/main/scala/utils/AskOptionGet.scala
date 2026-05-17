package cl.cadcc.ramitos.utils

import cats.mtl.Ask
import cats.syntax.all._
import cats.MonadThrow
import cats.Applicative

class AskOptionGet[F[_]: MonadThrow as F, E](val message: String, val underling: Ask[F, Option[E]]) extends Ask[F, E] {
    def applicative: Applicative[F] = implicitly

    def ask[E2 >: E]: F[E2] =
        underling.ask.flatMap {
            case None => F.raiseError(new IllegalAccessException(message))
            case Some(v) => v.pure
        }
}
