/*
 * Copyright 2018 Permutive
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.permutive.pubsub.producer.grpc

import com.google.cloud.pubsub.v1.Publisher

import scala.concurrent.duration._

case class PubsubProducerConfig[F[_]](
  batchSize: Long,
  delayThreshold: FiniteDuration,
  requestByteThreshold: Option[Long] = None,
  averageMessageSize: Long = 1024, // 1kB
  // modify publisher
  customizePublisher: Option[Publisher.Builder => Publisher.Builder] = None,
  // termination
  awaitTerminatePeriod: FiniteDuration = 30.seconds,
  onFailedTerminate: Throwable => F[Unit]
)
