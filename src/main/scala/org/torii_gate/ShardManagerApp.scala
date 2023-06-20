package org.torii_gate

import com.devsisters.shardcake.*
import com.devsisters.shardcake.interfaces.*
import org.torii_gate.config.*
import zio.*


object ShardManagerApp extends ZIOAppDefault {
  def run: Task[Nothing] =
    Server
      .run
      .provide(
        ZLayer.succeed(ManagerConfig.default),
        ZLayer.succeed(GrpcConfig.default),
        ZLayer.succeed(RedisConfig.default),
        RedisUriConfig.live,
        redis,
        StorageRedis.live,
        PodsHealth.local, // just ping a pod to see if it's alive
        GrpcPods.live, // use gRPC protocol
        ShardManager.live // Shard Manager logic
      )
}
