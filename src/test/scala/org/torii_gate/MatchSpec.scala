package org.torii_gate

import com.devsisters.shardcake.*
import com.devsisters.shardcake.StorageRedis.Redis
import com.devsisters.shardcake.interfaces.PodsHealth
import com.dimafeng.testcontainers.GenericContainer
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.pubsub.PubSub
import org.torii_gate.MatchBehavior
import org.torii_gate.MatchBehavior.Match
import org.torii_gate.MatchBehavior.MatchMakingError
import org.torii_gate.MatchBehavior.MatchMessage.Join
import org.torii_gate.config.MatchConfig
import sttp.client3.UriContext
import zio.{Config => _, _}
import zio.Clock.ClockLive
import zio.interop.catz.*
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.{sequential, withLiveClock}

object MatchEndToEndSpec extends ZIOSpecDefault {

  val shardManagerServer: ZLayer[ShardManager with ManagerConfig, Throwable, Unit] =
    ZLayer(Server.run.forkDaemon *> ClockLive.sleep(3.seconds).unit)

  val container: ZLayer[Any, Nothing, GenericContainer] =
    ZLayer.scoped {
      ZIO.acquireRelease {
        ZIO.attemptBlocking {
          val container =
            new GenericContainer(dockerImage = "redis:latest", exposedPorts = Seq(6379))
          container.start()
          container
        }.orDie
      }(container => ZIO.attemptBlocking(container.stop()).orDie)
    }

  val redis: ZLayer[GenericContainer, Throwable, Redis] =
    ZLayer.scopedEnvironment {
      implicit val runtime: zio.Runtime[Any] = zio.Runtime.default
      implicit val logger: Log[Task] = new Log[Task] {
        override def debug(msg: => String): Task[Unit] = ZIO.unit
        override def error(msg: => String): Task[Unit] = ZIO.logError(msg)
        override def info(msg: => String): Task[Unit] = ZIO.logDebug(msg)
      }

      ZIO
        .service[GenericContainer]
        .flatMap(container =>
          (for {
            client <- RedisClient[Task].from(
              s"redis://foobared@${container.host}:${container.mappedPort(container.exposedPorts.head)}"
            )
            commands <- Redis[Task].fromClient(client, RedisCodec.Utf8)
            pubSub <- PubSub.mkPubSubConnection[Task, String, String](client, RedisCodec.Utf8)
          } yield ZEnvironment(commands, pubSub)).toScopedZIO
        )
    }

  private val config = ZLayer.succeed(
    Config
      .default
      .copy(
        shardManagerUri = uri"http://localhost:8087/api/graphql",
        simulateRemotePods = true,
        sendTimeout = 3.seconds
      )
  )
  private val grpcConfig = ZLayer.succeed(GrpcConfig.default)
  private val managerConfig = ZLayer.succeed(ManagerConfig.default.copy(apiPort = 8087))
  private val redisConfig = ZLayer.succeed(RedisConfig.default)

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("MatchBehavior end to end test")(
      test("Send message to entities") {
        ZIO.scoped {
          for {
            _ <- Sharding.registerEntity(Match, MatchBehavior.behavior)
            _ <- Sharding.registerScoped
            matchShard <- Sharding.messenger(Match)
            _ <- matchShard.send("match1")(Join("user1", _))
            _ <- matchShard.send("match1")(Join("user2", _))
            _ <- matchShard.send("match1")(Join("user3", _))
            _ <- matchShard.send("match1")(Join("user4", _))
            members <- matchShard.send[Either[MatchMakingError, Set[String]]]("match1")(
              Join("user5", _)
            )
            failure <- matchShard.send[Either[MatchMakingError, Set[String]]]("match1")(
              Join("user6", _)
            )
          } yield assert(members)(isRight(hasSize(equalTo(5)))) &&
            assertTrue(failure.isLeft)
        }
      }
    ).provideShared(
      Sharding.live,
      KryoSerialization.live,
      GrpcPods.live,
      ShardManagerClient.liveWithSttp,
      StorageRedis.live,
      ShardManager.live,
      PodsHealth.noop,
      GrpcShardingService.live,
      shardManagerServer,
      container,
      redis,
      config,
      grpcConfig,
      managerConfig,
      redisConfig,
      MatchConfig.live
    ) @@ sequential @@ withLiveClock
}
