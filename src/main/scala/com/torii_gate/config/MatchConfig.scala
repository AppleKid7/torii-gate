package com.torii_gate.config

import zio.*
import zio.config.*
import zio.config.magnolia.descriptor
import zio.config.typesafe.TypesafeConfigSource

case class MatchConfig(maxNumberOfMembers: Int)

object MatchConfig {
  val live: ZLayer[Any, ReadError[String], MatchConfig] =
    ZLayer {
      read {
        descriptor[MatchConfig].from(
          TypesafeConfigSource.fromResourcePath.at(PropertyTreePath.$("MatchConfig"))
        )
      }
    }
}
