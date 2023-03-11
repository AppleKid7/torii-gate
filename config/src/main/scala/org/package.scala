package org

import com.devsisters.shardcake.StorageRedis.Redis
import org.torii_gate.config.RedisUriConfig
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.pubsub.PubSub
import zio.{Task, ZEnvironment, ZIO, ZLayer}
import zio.config.*
import zio.interop.catz.*

package object torii_gate {
  val redis: ZLayer[RedisUriConfig, Throwable, Redis] =
    ZLayer.scopedEnvironment[RedisUriConfig] {
      implicit val runtime: zio.Runtime[Any] = zio.Runtime.default
      implicit val logger: Log[Task] = new Log[Task] {
        override def debug(msg: => String): Task[Unit] = ZIO.logDebug(msg)
        override def error(msg: => String): Task[Unit] = ZIO.logError(msg)
        override def info(msg: => String): Task[Unit] = ZIO.logInfo(msg)
      }

      getConfig[RedisUriConfig].flatMap { config =>
        (for {
          client <- RedisClient[Task].from(
            s"${config.uri}${config.username}:${config.password}@${config.host}:${config.port}"
          )
          commands <- Redis[Task].fromClient(client, RedisCodec.Utf8)
          pubSub <- PubSub.mkPubSubConnection[Task, String, String](client, RedisCodec.Utf8)
        } yield ZEnvironment(commands, pubSub)).toScopedZIO
      }
    }

  extension [R, E, A](zio: ZIO[R, E, A]) {
    def debugThread: ZIO[R, E, A] =
      zio
        .tap(a => ZIO.succeed(println(s"[${Thread.currentThread().getName()}] $a")))
        .tapErrorCause(cause =>
          ZIO.succeed(println(s"[${Thread.currentThread().getName()}] [FAIL] $cause"))
        )
  }

  extension [R, E, A](zlayer: ZLayer[R, E, A]) {
    def debugThread: ZLayer[R, E, A] =
      zlayer
        .tap(a => ZIO.succeed(println(s"[${Thread.currentThread().getName()}] $a")))
        .tapErrorCause(cause =>
          ZIO.succeed(println(s"[${Thread.currentThread().getName()}] [FAIL] $cause"))
        )
  }
}
