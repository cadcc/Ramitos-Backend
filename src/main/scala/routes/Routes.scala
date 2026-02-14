package cl.cadcc.ramitos.routes

import cats.effect.IO
import cats.effect.kernel.Resource
import org.http4s.HttpRoutes
import alloy.SimpleRestJson
import smithy4s.http4s.SimpleRestJsonBuilder

val restRoutes: Resource[IO, HttpRoutes[IO]] =
    SimpleRestJsonBuilder
        .routes(WoofImpl[IO])
        .resource
