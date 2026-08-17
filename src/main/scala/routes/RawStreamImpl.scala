package cl.cadcc.ramitos.routes

import cats.syntax.all.*
import cats.effect.Sync
import fs2.Stream
import fs2.io.readInputStream
import smithy4s.time.Timestamp
import org.http4s.HttpRoutes
import org.http4s.implicits.*
import cats.Monad
import org.http4s.Response
import org.http4s.server.middleware.GZip
import org.http4s.EntityEncoder
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType

object RawStreamImpl {

    def routes[F[_]: Sync]: HttpRoutes[F] = Routes[F].routes

    private class Routes[F[_]: Sync as F] {
        val dsl = org.http4s.dsl.Http4sDsl[F]
        import dsl.*

        val routes: HttpRoutes[F] = HttpRoutes.of[F] {
            case GET -> Root / "api" / "courses.json" =>
                val stream = readInputStream(
                    F.blocking( getClass.getClassLoader.getResourceAsStream("courses.json") ),
                    chunkSize = 4096
                )
                Ok(stream).map(_.withContentType(`Content-Type`(MediaType.application.json)))
        }
    }
    
}
