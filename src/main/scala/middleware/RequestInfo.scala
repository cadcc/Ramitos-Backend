package cl.cadcc.ramitos.middleware

import org.typelevel.ci.CIStringSyntax
import cats.syntax.all._
import cats.effect.IO
import org.http4s.HttpRoutes
import cats.effect.IOLocal
import cats.MonadThrow
import org.http4s.Request
import com.comcast.ip4s.IpAddress
import cats.data.OptionT

case class RequestInfo(
    sourceIp: Option[IpAddress]
)

def getXFF[F[_]](request: Request[F]): Option[IpAddress] =
    for
        raw <- request.headers.get(ci"X-Forwarded-For")
        ip <- IpAddress.fromString(raw.head.value)
    yield ip

def getXRIP[F[_]](request: Request[F]): Option[IpAddress] =
    for
        raw <- request.headers.get(ci"X-Real-IP")
        ip <- IpAddress.fromString(raw.head.value)
    yield ip

def getSourceIp[F[_]](request: Request[F]): Option[IpAddress] =
    request.remoteAddr

def withRequestInfo(routes: HttpRoutes[IO], local: IOLocal[RequestInfo]): HttpRoutes[IO] =
    HttpRoutes { request =>
        val sourceIp =
            getXFF(request)
                .orElse(getXRIP(request))
                .orElse(getSourceIp(request))
        
        val requestInfo = RequestInfo(sourceIp)
        
        OptionT.liftF( local.set(requestInfo) ) *> routes(request)
    }
