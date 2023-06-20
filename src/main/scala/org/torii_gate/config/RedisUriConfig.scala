package org.torii_gate.config

import zio.*
import zio.config.*
import zio.config.magnolia.descriptor
import zio.config.typesafe.TypesafeConfigSource

case class RedisUriConfig(uri: String, host: String, username: String, password: String, port: Int)

object RedisUriConfig {
  val live: ZLayer[Any, ReadError[String], RedisUriConfig] =
    ZLayer {
      read {
        descriptor[RedisUriConfig].from(
          TypesafeConfigSource.fromResourcePath.at(PropertyTreePath.$("RedisUriConfig"))
        )
      }
    }
}
