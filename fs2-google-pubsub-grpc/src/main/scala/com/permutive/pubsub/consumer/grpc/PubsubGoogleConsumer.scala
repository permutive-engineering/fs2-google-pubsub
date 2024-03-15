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

import cats.Applicative
import cats.effect.kernel.Sync
import cats.syntax.all._
import com.google.pubsub.v1.PubsubMessage
import com.permutive.pubsub.consumer.decoder.MessageDecoder
import com.permutive.pubsub.consumer.grpc.internal.PubsubSubscriber
import com.permutive.pubsub.consumer.{ConsumerRecord, Model}
import fs2.Stream

import scala.jdk.CollectionConverters._
import scala.util.control.NoStackTrace

object PubsubGoogleConsumer {

  /** Indicates the underlying Java PubSub consumer has failed.
    *
    * @param cause the cause of the failure
    */
  case class InternalPubSubError(cause: Throwable)
      extends Throwable("Internal Java PubSub consumer failed", cause)
      with NoStackTrace

  /** Subscribe with manual acknowledgement
    *
    * The stream fails with an [[InternalPubSubError]] if the underlying Java consumer fails.
    *
    * @param projectId    google cloud project id
    * @param subscription name of the subscription
    * @param errorHandler upon failure to decode, an exception is thrown. Allows acknowledging the message.
    */
  final def subscribe[F[_]: Sync, A: MessageDecoder](
    projectId: Model.ProjectId,
    subscription: Model.Subscription,
    errorHandler: (PubsubMessage, Throwable, F[Unit], F[Unit]) => F[Unit],
    config: PubsubGoogleConsumerConfig[F]
  ): Stream[F, ConsumerRecord[F, A]] =
    subscribeDecode[F, A, ConsumerRecord[F, A]](
      projectId,
      subscription,
      errorHandler,
      config,
      onDecode = (record, value) =>
        Applicative[F].pure(
          ConsumerRecord(
            value,
            record.value.getAttributesMap.asScala.toMap,
            record.ack,
            record.nack,
            _ => Applicative[F].unit
          )
        ),
    )

  /** Subscribe with automatic acknowledgement
    *
    * The stream fails with an [[InternalPubSubError]] if the underlying Java consumer fails.
    *
    * @param projectId    google cloud project id
    * @param subscription name of the subscription
    * @param errorHandler upon failure to decode, an exception is thrown. Allows acknowledging the message.
    */
  final def subscribeAndAck[F[_]: Sync, A: MessageDecoder](
    projectId: Model.ProjectId,
    subscription: Model.Subscription,
    errorHandler: (PubsubMessage, Throwable, F[Unit], F[Unit]) => F[Unit],
    config: PubsubGoogleConsumerConfig[F]
  ): Stream[F, A] =
    subscribeDecode[F, A, A](
      projectId,
      subscription,
      errorHandler,
      config,
      onDecode = (record, value) => record.ack.as(value),
    )

  /** Subscribe to the raw stream, receiving the the message as retrieved from PubSub
    *
    * The stream fails with an [[InternalPubSubError]] if the underlying Java consumer fails.
    */
  final def subscribeRaw[F[_]: Sync](
    projectId: Model.ProjectId,
    subscription: Model.Subscription,
    config: PubsubGoogleConsumerConfig[F]
  ): Stream[F, ConsumerRecord[F, PubsubMessage]] =
    PubsubSubscriber
      .subscribe(projectId, subscription, config)
      .map(msg =>
        ConsumerRecord(msg.value, msg.value.getAttributesMap.asScala.toMap, msg.ack, msg.nack, _ => Applicative[F].unit)
      )

  private def subscribeDecode[F[_]: Sync, A: MessageDecoder, B](
    projectId: Model.ProjectId,
    subscription: Model.Subscription,
    errorHandler: (PubsubMessage, Throwable, F[Unit], F[Unit]) => F[Unit],
    config: PubsubGoogleConsumerConfig[F],
    onDecode: (internal.Model.Record[F], A) => F[B],
  ): Stream[F, B] =
    PubsubSubscriber
      .subscribe(projectId, subscription, config)
      .evalMapChunk[F, Option[B]](record =>
        MessageDecoder[A].decode(record.value.getData.toByteArray) match {
          case Left(e)  => errorHandler(record.value, e, record.ack, record.ack).as(None)
          case Right(v) => onDecode(record, v).map(Some(_))
        }
      )
      .unNone
}
