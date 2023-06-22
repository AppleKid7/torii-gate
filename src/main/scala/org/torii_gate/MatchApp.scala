package org.torii_gate

import com.devsisters.shardcake
import com.devsisters.shardcake.*
import com.devsisters.shardcake.interfaces.*
import dev.profunktor.redis4cats.RedisCommands
import java.util.UUID.randomUUID
import org.torii_gate.MatchBehavior.*
import org.torii_gate.MatchBehavior.MatchMessage.*
import org.torii_gate.config.*
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
  val config: ZLayer[ShardcakeConfig, SecurityException, shardcake.Config] =
    ZLayer(getConfig[ShardcakeConfig].map { config =>
      shardcake.Config.default.copy(shardingPort = config.port)
    })

  val app: Http[Scope & Sharding & Session, MatchMakingError, Request, Response] =
    Http.collectZIO[Request] {
      case Method.GET -> !! / "text" =>
        ZIO.unit.map(_ => Response.text("Hello World!"))
      case Method.GET -> !! / "sessions" =>
        Session.getAllSessions map { sessionIds =>
          Response
            .json(SessionListResponse(sessionIds.iterator.map(_.id).toList).toJson)
            .setStatus(Status.Ok)
        }
      case Method.POST -> !! / "sessions" =>
        Session.createSession() map { sessionId =>
          Response.json(CreateSessionResponse(sessionId).toJson).setStatus(Status.Ok)
        }
      case req @ (Method.CUSTOM("JOIN") -> !! / "sessions" / sessionId) =>
        Session.joinSession(SessionId(sessionId)) map { _ =>
          Response
            .json(JoinSessionResponse(SessionId(sessionId), "Joined succesfully!").toJson)
            .setStatus(Status.Ok)
        }
      case req @ (Method.CUSTOM("LEAVE") -> !! / "sessions" / sessionId) =>
        for {
          either <- req
            .bodyAsString
            .mapError(e => MatchMakingError.NetworkReadError(e.getMessage()))
            .map(_.fromJson[UserId])
          data <- ZIO.fromEither(either).mapError(e => MatchMakingError.InvalidJson(e))
          userId = UserId(data.id)
          res <- Session.leaveSession(SessionId(sessionId), userId)
        } yield Response.json(LeaveSessionResponse(res).toJson).setStatus(Status.Ok)
      case req @ (Method.GET -> !! / "sessions" / sessionId / "users") =>
        for {
          ids <- Session.getAllUsers(SessionId(sessionId))
        } yield Response
          .json(UserListResponse(ids.iterator.map(_.id).toList).toJson)
          .setStatus(Status.Ok)
    }

  private val server =
    Server.paranoidLeakDetection ++ // Paranoid leak detection (affects performance)
      Server.app(
        app.catchAll(ex =>
          Http.succeed(
            Response.json(FailedResponse(ex.message).toJson).setStatus(Status.BadRequest)
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
          MatchConfig.live,
          Session.live
        )
      }
      .provide(AppConfig.live)
  }
}
