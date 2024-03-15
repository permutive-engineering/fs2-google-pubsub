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

package com.permutive.pubsub.consumer.grpc

import com.google.cloud.pubsub.v1.Subscriber

import scala.concurrent.duration._

/** Pubsub subscriber config.
  *
  * @param maxQueueSize configures two options: the max size of the backing queue, and, the "max outstanding element count" option of Pubsub
  * @param parallelPullCount number of parallel pullers, see [[https://javadoc.io/static/com.google.cloud/google-cloud-pubsub/1.100.0/com/google/cloud/pubsub/v1/Subscriber.Builder.html#setParallelPullCount-int-]]
  * @param maxAckExtensionPeriod see [[https://javadoc.io/static/com.google.cloud/google-cloud-pubsub/1.100.0/com/google/cloud/pubsub/v1/Subscriber.Builder.html#setMaxAckExtensionPeriod-org.threeten.bp.Duration-]]
  * @param awaitTerminatePeriod if the underlying PubSub subcriber fails to terminate cleanly, how long do we wait until it's forcibly timed out.
  * @param onFailedTerminate upon failure to terminate, call this function
  * @param customizeSubscriber optionally, provide a function that allows full customisation of the underlying Java Subscriber object.
  */
case class PubsubGoogleConsumerConfig[F[_]](
  maxQueueSize: Int = 1000,
  parallelPullCount: Int = 3,
  maxAckExtensionPeriod: FiniteDuration = 10.seconds,
  awaitTerminatePeriod: FiniteDuration = 30.seconds,
  onFailedTerminate: Throwable => F[Unit],
  customizeSubscriber: Option[Subscriber.Builder => Subscriber.Builder] = None
)
