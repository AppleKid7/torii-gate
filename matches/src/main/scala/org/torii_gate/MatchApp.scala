package org.torii_gate

import com.devsisters.shardcake
import com.devsisters.shardcake.*
import com.devsisters.shardcake.interfaces.*
import org.torii_gate.MatchBehavior.*
import org.torii_gate.MatchBehavior.MatchMessage.*
import org.torii_gate.config.*
import dev.profunktor.redis4cats.RedisCommands
import io.getquill.*
import io.getquill.jdbczio.Quill
import java.util.UUID.randomUUID
import scala.util.{Failure, Success, Try}
import sttp.client3.httpclient.zio.*
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler
import sttp.tapir.server.ziohttp.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir.*
import zio.*
import zio.config.*
import zio.json.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.swagger.SwaggerUI
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir.docs.openapi._

object MatchApp extends ZIOAppDefault {
  val config: ZLayer[ShardcakeConfig, SecurityException, shardcake.Config] =
    ZLayer(getConfig[ShardcakeConfig].map { config =>
      shardcake.Config.default.copy(shardingPort = config.port)
    })

  /*
  case Method.GET -> !! / "text" =>
      ZIO.unit.map(_ => Response.text("Hello World!"))
    case Method.POST -> !! / "join" =>
      (for {
        matchShard <- Sharding.messenger(MatchBehavior.Match)
        res <- matchShard
          .send[Either[MatchMakingError, Set[String]]](s"match1")(Join(s"user-${randomUUID()}", _))
          .orDie
        value <- ZIO.fromEither(res)
      } yield Response.json(UserJoinResponse(value.toList).toJson).setStatus(Status.Ok))
    case req @ (Method.POST -> !! / "leave") =>
   */

  val joinEndpoint: PublicEndpoint[Unit, MatchMakingError, UserJoinResponse, Any] =
    sttp
      .tapir
      .endpoint
      .post
      .in("join")
      .out(jsonBody[UserJoinResponse])
      .errorOut(jsonBody[MatchMakingError])

  val joinServerEndpoint: ZServerEndpoint[Sharding, Any] =
    joinEndpoint.zServerLogic(_ =>
      val test: ZIO[Sharding, MatchMakingError, UserJoinResponse] = for {
        matchShard <- Sharding.messenger(MatchBehavior.Match)
        res <- matchShard
          .send[Either[MatchMakingError, Set[String]]](s"match1")(Join(s"user-${randomUUID()}", _))
          .orDie
        value <- ZIO.fromEither(res)
      } yield UserJoinResponse(value.toList)
      test
    )

  private val register = for {
    _ <- Sharding.registerEntity(
      MatchBehavior.Match,
      MatchBehavior.behavior
    )
    _ <- Sharding.registerScoped
  } yield ()

  val myDecodeFailureHandler = DefaultDecodeFailureHandler
    .default
    .copy(
      respond = DefaultDecodeFailureHandler.respond(
        _,
        badRequestOnPathErrorIfPathShapeMatches = true,
        badRequestOnPathInvalidIfPathShapeMatches = true
      )
    )
  val joinEndpointOptions: ZioHttpServerOptions[Sharding] = ZioHttpServerOptions
    .customiseInterceptors[Sharding]
    .decodeFailureHandler(myDecodeFailureHandler)
    .options

  val swaggerEndpoints: List[ZServerEndpoint[Sharding, Any]] = SwaggerInterpreter().fromServerEndpoints(List(joinServerEndpoint), "Matches", "1.0")
  // val docs = List(joinEndpoint).toOpenAPI("Matches", "1.0")
  //  val swaggerUIRoute: List[ServerEndpoint[Sharding, Future]] = SwaggerUI[Future](docsAsYaml)
  // val routes = ZioHttpInterpreter().toHttp[Sharding](List(joinServerEndpoint) ++ swaggerUIRoute)
  val routes = ZioHttpInterpreter().toHttp[Sharding](List(joinServerEndpoint) ++ swaggerEndpoints)

  private val server: URIO[Sharding & http.Server, Nothing] =
    zio
      .http
      .Server
      .serve(
        routes // .catchAll(ex =>
        //   zio
        //     .http
        //     .Http
        //     .succeed(
        //       zio.http.Response.json(s"""{"failure": "${ex}"}""")
        //     )
        // )
      ) // Setup the Http app

  val run = getConfig[AppConfig]
    .flatMap { c =>
      (register *> (zio
        .http
        .Server
        .serve(routes)
        .flatMap(_ => Console.printLine(s"Server started")) *> ZIO.never))
        .provide(
          zio.http.ServerConfig.live(zio.http.ServerConfig.default.port(c.port)),
          zio.http.Server.live,
          ZLayer.succeed(RedisConfig.default),
          RedisUriConfig.live,
          redis,
          StorageRedis.live,
          KryoSerialization.live,
          Sharding.live,
          GrpcShardingService.live,
          ZLayer.succeed(GrpcConfig.default),
          GrpcPods.live,
          config,
          // sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend.layer(),
          // ShardManagerClient.live,
          ShardManagerClient.liveWithSttp.debugThread,
          MatchConfig.live,
          ShardcakeConfig.live,
          Scope.default
        )
        .exitCode
    }
    .provide(AppConfig.live)
}
