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

package com.permutive.pubsub.consumer.http

import cats.Applicative
import cats.effect.kernel.Async
import cats.syntax.all._
import com.permutive.pubsub.consumer.ConsumerRecord
import com.permutive.pubsub.consumer.Model.{ProjectId, Subscription}
import com.permutive.pubsub.consumer.decoder.MessageDecoder
import com.permutive.pubsub.consumer.http.internal.Model.InternalRecord
import com.permutive.pubsub.consumer.http.internal.PubsubSubscriber
import fs2.Stream
import org.typelevel.log4cats.Logger
import org.http4s.client.Client
import org.http4s.client.middleware.RetryPolicy
import org.http4s.client.middleware.RetryPolicy.{exponentialBackoff, recklesslyRetriable}

import java.util.Base64
import scala.concurrent.duration._

object PubsubHttpConsumer {

  /** Subscribe with manual acknowledgement
    *
    * @param projectId          google cloud project id
    * @param subscription       name of the subscription
    * @param serviceAccountPath path to the Google Service account file (json), if not specified then the GCP metadata
    *                           endpoint is used to retrieve the `default` service account access token
    * @param errorHandler       upon failure to decode, an exception is thrown. Allows acknowledging the message.
    *
    * See the following for documentation on GCP metadata endpoint and service accounts:
    *  - https://cloud.google.com/compute/docs/storing-retrieving-metadata
    *  - https://cloud.google.com/compute/docs/metadata/default-metadata-values
    *  - https://cloud.google.com/compute/docs/access/create-enable-service-accounts-for-instances
    */
  final def subscribe[F[_]: Async: Logger, A: MessageDecoder](
    projectId: ProjectId,
    subscription: Subscription,
    serviceAccountPath: Option[String],
    config: PubsubHttpConsumerConfig[F],
    httpClient: Client[F],
    errorHandler: (PubsubMessage, Throwable, F[Unit], F[Unit]) => F[Unit],
    httpClientRetryPolicy: RetryPolicy[F] = recklesslyRetryPolicy[F]
  ): Stream[F, ConsumerRecord[F, A]] =
    subscribeDecode[F, A, ConsumerRecord[F, A]](
      projectId,
      subscription,
      serviceAccountPath,
      config,
      httpClient,
      errorHandler,
      httpClientRetryPolicy,
      onDecode = (record, value) => Applicative[F].pure(record.toConsumerRecord(value)),
    )

  /** Subscribe with automatic acknowledgement
    *
    * @param projectId          google cloud project id
    * @param subscription       name of the subscription
    * @param serviceAccountPath path to the Google Service account file (json), if not specified then the GCP metadata
    *                           endpoint is used to retrieve the `default` service account access token
    * @param errorHandler       upon failure to decode, an exception is thrown. Allows acknowledging the message.
    *
    * See the following for documentation on GCP metadata endpoint and service accounts:
    *  - https://cloud.google.com/compute/docs/storing-retrieving-metadata
    *  - https://cloud.google.com/compute/docs/metadata/default-metadata-values
    *  - https://cloud.google.com/compute/docs/access/create-enable-service-accounts-for-instances
    */
  final def subscribeAndAck[F[_]: Async: Logger, A: MessageDecoder](
    projectId: ProjectId,
    subscription: Subscription,
    serviceAccountPath: Option[String],
    config: PubsubHttpConsumerConfig[F],
    httpClient: Client[F],
    errorHandler: (PubsubMessage, Throwable, F[Unit], F[Unit]) => F[Unit],
    httpClientRetryPolicy: RetryPolicy[F] = recklesslyRetryPolicy[F]
  ): Stream[F, A] =
    subscribeDecode[F, A, A](
      projectId,
      subscription,
      serviceAccountPath,
      config,
      httpClient,
      errorHandler,
      httpClientRetryPolicy,
      onDecode = (record, value) => record.ack.as(value),
    )

  /** Subscribe to the raw stream, receiving the the message as retrieved from PubSub
    *
    * @param projectId          google cloud project id
    * @param subscription       name of the subscription
    * @param serviceAccountPath path to the Google Service account file (json), if not specified then the GCP metadata
    *                           endpoint is used to retrieve the `default` service account access token
    *
    * See the following for documentation on GCP metadata endpoint and service accounts:
    *  - https://cloud.google.com/compute/docs/storing-retrieving-metadata
    *  - https://cloud.google.com/compute/docs/metadata/default-metadata-values
    *  - https://cloud.google.com/compute/docs/access/create-enable-service-accounts-for-instances
    */
  final def subscribeRaw[F[_]: Async: Logger](
    projectId: ProjectId,
    subscription: Subscription,
    serviceAccountPath: Option[String],
    config: PubsubHttpConsumerConfig[F],
    httpClient: Client[F],
    httpClientRetryPolicy: RetryPolicy[F] = recklesslyRetryPolicy[F],
  ): Stream[F, ConsumerRecord[F, PubsubMessage]] =
    PubsubSubscriber
      .subscribe(projectId, subscription, serviceAccountPath, config, httpClient, httpClientRetryPolicy)
      .map(msg => msg.toConsumerRecord(msg.value))

  /*
    Pub/Sub requests are `POST` and thus are not considered idempotent by http4s, therefore we must
    use a different retry behaviour than the default.
   */
  def recklesslyRetryPolicy[F[_]]: RetryPolicy[F] =
    RetryPolicy(exponentialBackoff(maxWait = 5.seconds, maxRetry = 3), (_, result) => recklesslyRetriable(result))

  private def subscribeDecode[F[_]: Async: Logger, A: MessageDecoder, B](
    projectId: ProjectId,
    subscription: Subscription,
    serviceAccountPath: Option[String],
    config: PubsubHttpConsumerConfig[F],
    httpClient: Client[F],
    errorHandler: (PubsubMessage, Throwable, F[Unit], F[Unit]) => F[Unit],
    httpClientRetryPolicy: RetryPolicy[F],
    onDecode: (InternalRecord[F], A) => F[B],
  ): Stream[F, B] =
    PubsubSubscriber
      .subscribe(projectId, subscription, serviceAccountPath, config, httpClient, httpClientRetryPolicy)
      .evalMapChunk[F, Option[B]](record =>
        MessageDecoder[A].decode(Base64.getDecoder.decode(record.value.data.getBytes)) match {
          case Left(e)  => errorHandler(record.value, e, record.ack, record.nack).as(None)
          case Right(v) => onDecode(record, v).map(Some(_))
        }
      )
      .unNone
}
