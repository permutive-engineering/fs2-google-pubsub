package com.permutive.pubsub.consumer.grpc

import cats.Applicative
import cats.effect.kernel.Sync
import cats.syntax.all._
import com.google.pubsub.v1.PubsubMessage
import com.permutive.pubsub.consumer.decoder.MessageDecoder
import com.permutive.pubsub.consumer.grpc.internal.PubsubSubscriber
import com.permutive.pubsub.consumer.{ConsumerRecord, Model}
import fs2.Stream

object PubsubGoogleConsumer {

  /**
    * Subscribe with manual acknowledgement
    *
    * @param blocker
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
    PubsubSubscriber
      .subscribe(projectId, subscription, config)
      .flatMap { case internal.Model.Record(msg, ack, nack) =>
        MessageDecoder[A].decode(msg.getData.toByteArray) match {
          case Left(e)  => Stream.exec(errorHandler(msg, e, ack, nack))
          case Right(v) => Stream.emit(ConsumerRecord(v, ack, nack, _ => Applicative[F].unit))
        }
      }

  /**
    * Subscribe with automatic acknowledgement
    *
    * @param blocker
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
    PubsubSubscriber
      .subscribe(projectId, subscription, config)
      .flatMap { case internal.Model.Record(msg, ack, nack) =>
        MessageDecoder[A].decode(msg.getData.toByteArray) match {
          case Left(e)  => Stream.exec(errorHandler(msg, e, ack, nack))
          case Right(v) => Stream.eval(ack >> v.pure)
        }
      }

  /**
    * Subscribe to the raw stream, receiving the the message as retrieved from PubSub
    */
  final def subscribeRaw[F[_]: Sync](
    projectId: Model.ProjectId,
    subscription: Model.Subscription,
    config: PubsubGoogleConsumerConfig[F]
  ): Stream[F, ConsumerRecord[F, PubsubMessage]] =
    PubsubSubscriber
      .subscribe(projectId, subscription, config)
      .map(msg => ConsumerRecord(msg.value, msg.ack, msg.nack, _ => Applicative[F].unit))
}
