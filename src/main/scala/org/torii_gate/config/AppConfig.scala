package org.torii_gate.config

import zio.*
import zio.config.*
import zio.config.magnolia.descriptor
import zio.config.typesafe.TypesafeConfigSource

case class AppConfig(port: Int)

object AppConfig {
  val live: ZLayer[Any, ReadError[String], AppConfig] =
    ZLayer {
      read {
        descriptor[AppConfig].from(
          TypesafeConfigSource.fromResourcePath.at(PropertyTreePath.$("AppConfig"))
        )
      }
    }
}
