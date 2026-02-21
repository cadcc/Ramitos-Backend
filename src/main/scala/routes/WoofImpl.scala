package cl.cadcc.ramitos.routes

import cl.cadcc.ramitos.schema.WoofService
import cl.cadcc.ramitos.schema.WoofOutput
import cats.syntax.all._
import cats.MonadThrow

class WoofImpl[F[_]: MonadThrow] extends WoofService[F]:

  override def woof(): F[WoofOutput] = WoofOutput("Woof Woof!").pure
