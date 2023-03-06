package com.torii_gate

import com.devsisters.shardcake
import com.devsisters.shardcake.*
import com.devsisters.shardcake.interfaces.*
import dev.profunktor.redis4cats.RedisCommands
import io.getquill.*
import io.getquill.jdbczio.Quill
import java.util.UUID.randomUUID
import com.torii_gate.MatchBehavior.*
import com.torii_gate.MatchBehavior.MatchMessage.*
import com.torii_gate.config.*
import scala.util.{Failure, Success, Try}
import zhttp.http.*
import zhttp.service
import zhttp.service.{EventLoopGroup, Server}
import zhttp.service.ChannelFactory
import zhttp.service.Server
import zhttp.service.server.ServerChannelFactory
import zio.*
import zio.config.*
import zio.json.*

object MatchApp extends ZIOAppDefault {
  // val config: ZLayer[Any, SecurityException, shardcake.Config] =
  //   ZLayer(
  //     System
  //       .env("port")
  //       .map(
  //         _.flatMap(_.toIntOption).fold(shardcake.Config.default)(port =>
  //           shardcake.Config.default.copy(shardingPort = port)
  //         )
  //       )
  //   )
  val config: ZLayer[ShardcakeConfig, SecurityException, shardcake.Config] =
    ZLayer(getConfig[ShardcakeConfig].map { config =>
      shardcake.Config.default.copy(shardingPort = config.port)
    })

  val app: Http[Scope & Sharding, MatchMakingError, Request, Response] = Http.collectZIO[Request] {
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
      for {
        either <- req
          .bodyAsString
          .mapError(e => MatchMakingError.NetworkReadError(e.getMessage()))
          .map(_.fromJson[UserLeave])
        data <- ZIO.fromEither(either).mapError(e => MatchMakingError.InvalidJson(e))
        matchShard <- Sharding.messenger(MatchBehavior.Match)
        res <- matchShard
          .send[Either[MatchMakingError, String]](s"match1")(
            Leave(s"user-${data.id}", _)
          )
          .mapError(e => MatchMakingError.ShardcakeConnectionError(e.getMessage()))
        value <- ZIO.fromEither(res)
      } yield Response.json(s"""{"success": "$value"}""").setStatus(Status.Ok)
  }

  private val server =
    Server.paranoidLeakDetection ++ // Paranoid leak detection (affects performance)
      Server.app(
        app.catchAll(ex =>
          Http.succeed(
            Response.json(s"""{"failure": "${ex.message}"}""").setStatus(Status.BadRequest)
          )
        )
      ) // Setup the Http app

  private val register = for {
    _ <- Sharding.registerEntity(
      MatchBehavior.Match,
      MatchBehavior.behavior
    )
    _ <- Sharding.registerScoped
  } yield ()

  val run: ZIO[Environment & (ZIOAppArgs & Scope), Any, Any] = ZIOAppArgs.getArgs.flatMap { args =>
    // Configure thread count using CLI
    val nThreads: Int = args.headOption.flatMap(x => Try(x.toInt).toOption).getOrElse(0)

    // Create a new server
    getConfig[AppConfig]
      .flatMap { c =>
        (register *>
          server
            .withPort(c.port)
            .make
            .flatMap(start =>
              // Waiting for the server to start then make sure it stays up forever with ZIO.never
              Console.printLine(s"Server started on port ${start.port}") *> ZIO.never,
            )).provide(
          ShardcakeConfig.live,
          config,
          ServerChannelFactory.auto,
          EventLoopGroup.auto(nThreads),
          Scope.default,
          ZLayer.succeed(GrpcConfig.default),
          ZLayer.succeed(RedisConfig.default),
          RedisUriConfig.live,
          redis,
          KryoSerialization.live,
          StorageRedis.live,
          ShardManagerClient.liveWithSttp,
          GrpcPods.live,
          Sharding.live,
          GrpcShardingService.live,
          MatchConfig.live
        )
      }
      .provide(AppConfig.live)
  }
}
