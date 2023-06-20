package org.torii_gate.config

import zio._
import zio.config._
import zio.config.magnolia.descriptor
import zio.config.typesafe.TypesafeConfigSource

case class ShardcakeConfig(port: Int)

object ShardcakeConfig {
  val live: ZLayer[Any, ReadError[String], ShardcakeConfig] =
    ZLayer {
      read {
        descriptor[ShardcakeConfig].from(
          TypesafeConfigSource.fromResourcePath.at(PropertyTreePath.$("ShardcakeConfig"))
        )
      }
    }
}
