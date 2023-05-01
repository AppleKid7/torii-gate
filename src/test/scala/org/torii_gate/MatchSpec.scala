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
import scala.reflect.runtime.universe.*
import com.devsisters.shardcake.interfaces.Serialization
import com.twitter.chill.{ KryoInstantiator, KryoPool, ScalaKryoInstantiator }
import scala.collection.immutable
import zio.prelude.ParSeq

object MatchEndToEndSpec extends ZIOSpecDefault {
  
  object TestKryoSerialization {
    val live: ZLayer[Any, Throwable, Serialization] =
      ZLayer {
        ZIO.attempt {
          def kryoInstantiator: KryoInstantiator = (new ScalaKryoInstantiator).setRegistrationRequired(false)
          def poolSize: Int                      = 4 * java.lang.Runtime.getRuntime.availableProcessors
          KryoPool.withByteArrayOutputStream(poolSize, kryoInstantiator)
        }.map(kryoPool =>
          new Serialization {
            def encode(message: Any): Task[Array[Byte]] = ZIO.attempt(kryoPool.toBytesWithClass(message))
            def decode[A](bytes: Array[Byte]): Task[A]  = ZIO.attempt(kryoPool.fromBytes(bytes).asInstanceOf[A])
          }
        )
      }
  }

  // object TestScodecSerialization {
  //   val live: ZLayer[Any, Throwable, Serialization] =
  //     ZLayer {
  //       ZIO.attempt {
  //         val anyCodec: Codec[Any] = new Codec[Any] {
  //           def encode(any: Any): Attempt[BitVector] = any match {
  //             case i: Int => int32.encode(i)
  //             case l: Long => int64.encode(l)
  //             case s: String => utf8_32.encode(s)
  //             case _ => Attempt.failure(Err("Unsupported type"))
  //           }

  //           def decode(bits: BitVector): Attempt[DecodeResult[Any]] = {
  //             int32.decode(bits).flatMap { iResult =>
  //               val i = iResult.value
  //               if (i == 0) Attempt.successful(DecodeResult(null, iResult.remainder))
  //               else if (i > 0) {
  //                 utf8_32.decode(iResult.remainder).map { sResult =>
  //                   DecodeResult(sResult.value, sResult.remainder)
  //                 }
  //               } else {
  //                 int64.decode(iResult.remainder).map { lResult =>
  //                   DecodeResult(lResult.value, lResult.remainder)
  //                 }
  //               }
  //             }
  //           }

  //           def sizeBound = SizeBound.unknown
  //         }
  //         anyCodec
  //       }.map(anyCodec =>
  //         new Serialization{
  //           def encode(message: Any): Task[Array[Byte]] = ZIO.attempt {
  //             val encoded: Attempt[BitVector] = anyCodec.encode(message)
  //             encoded.getOrElse(BitVector.empty).toByteArray
  //           }
  //           def decode[A](bytes: Array[Byte]): Task[A] = ZIO.attempt {
  //             val bits = BitVector(bytes)
  //             // val decoded: Attempt[DecodeResult[A]] = anyCodec.decode(bits).map(result => result.map(_.asInstanceOf[A]))
  //             val decoded = anyCodec.decode(bits)
  //             decoded.getOrElse(throw new java.lang.Exception("Failed to decode bytes")).value.asInstanceOf[A]
  //           }
  //         }
  //       )
  //     }
  // }

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

  def spec: Spec[TestEnvironment with zio.Scope, Any] =
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
      TestKryoSerialization.live,
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
