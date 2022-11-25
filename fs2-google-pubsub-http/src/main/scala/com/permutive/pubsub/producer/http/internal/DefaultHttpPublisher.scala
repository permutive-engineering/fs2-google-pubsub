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

package com.permutive.pubsub.producer.http.internal

import alleycats.syntax.foldable._
import cats.effect.kernel._
import cats.syntax.all._
import cats.{Applicative, Foldable, Traverse}
import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._
import com.permutive.pubsub.http.oauth._
import com.permutive.pubsub.http.util.RefreshableEffect
import com.permutive.pubsub.producer.Model.MessageId
import com.permutive.pubsub.producer.encoder.MessageEncoder
import com.permutive.pubsub.producer.http.PubsubHttpProducerConfig
import com.permutive.pubsub.producer.{Model, PubsubProducer}
import org.http4s.Method._
import org.http4s.Uri._
import org.http4s._
import org.http4s.client._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers._
import org.typelevel.log4cats.Logger

import java.util.Base64
import scala.concurrent.duration._

private[http] class DefaultHttpPublisher[F[_]: Async: Logger, A: MessageEncoder] private (
  baseApiUrl: Uri,
  client: Client[F],
  requestAuthorizer: RequestAuthorizer[F],
) extends PubsubProducer[F, A]
    with Http4sClientDsl[F] {
  import DefaultHttpPublisher._

  final private[this] val publishRoute =
    Uri.unsafeFromString(s"${baseApiUrl.renderString}:publish")

  final override def produce(data: A, attributes: Map[String, String], uniqueId: String): F[MessageId] =
    produceMany[List](List(Model.SimpleRecord(data, attributes, uniqueId))).map(_.head)

  final override def produceMany[G[_]: Traverse](records: G[Model.Record[A]]): F[List[MessageId]] =
    for {
      msgs <- records.traverse(recordToMessage)
      json <- Sync[F].delay(writeToArray(MessageBundle(msgs)))
      resp <- sendHttpRequest(json)
    } yield resp

  private def sendHttpRequest(json: Array[Byte]): F[List[MessageId]] = {
    val req = POST(json, publishRoute, `Content-Type`(MediaType.application.json))
    for {
      authedReq <- requestAuthorizer.authorize(req)
      resp      <- client.expectOr[Array[Byte]](authedReq)(onError)
      decoded <- Sync[F].delay(readFromArray[MessageIds](resp)).onError { case _ =>
        Logger[F].error(s"Publish response from PubSub was invalid. Body: ${new String(resp)}")
      }
    } yield decoded.messageIds
  }

  @inline
  private def recordToMessage(record: Model.Record[A]): F[Message] =
    MessageEncoder[A]
      .encode(record.data)
      .map(toMessage(_, record.uniqueId, record.attributes))
      .liftTo[F]

  @inline
  private def toMessage(bytes: Array[Byte], uniqueId: String, attributes: Map[String, String]): Message =
    Message(
      data = Base64.getEncoder.encodeToString(bytes),
      messageId = uniqueId,
      attributes = attributes
    )

  @inline
  private def onError(resp: Response[F]): F[Throwable] =
    resp.as[String].map(FailedRequestToPubsub(resp.status, _))
}

private[http] object DefaultHttpPublisher {

  def resource[F[_]: Async: Logger, A: MessageEncoder](
    projectId: Model.ProjectId,
    topic: Model.Topic,
    maybeTokenProvider: Option[TokenProvider[F]],
    config: PubsubHttpProducerConfig[F],
    httpClient: Client[F]
  ): Resource[F, PubsubProducer[F, A]] =
    for {
      tokenProvider <-
        if (config.isEmulator) Resource.pure[F, TokenProvider[F]](DefaultTokenProvider.noAuth)
        else
          maybeTokenProvider.fold(
            CachedTokenProvider
              .resource(
                DefaultTokenProvider.instanceMetadata(httpClient),
                // GCP metadata endpoint caches tokens until 5 minutes before expiry.
                // Wait until 4 minutes before expiry to refresh the token in this library. That should ensure a new
                // token will be provided and have no risk of any requests using an expired token.
                // See: https://cloud.google.com/compute/docs/access/create-enable-service-accounts-for-instances#applications
                // "The metadata server caches access tokens until they have 5 minutes of remaining time before they expire."
                safetyPeriod = 4.minutes,
                backgroundFailureHook = config.onTokenRetriesExhausted,
                onNewToken = config.onTokenRefreshSuccess.map(onRefreshSuccess =>
                  (_: AccessToken, _: FiniteDuration) => onRefreshSuccess
                ),
              )
          )(tokenProvider =>
            for {
              accessTokenRefEffect <- RefreshableEffect.createRetryResource(
                refresh = tokenProvider.accessToken,
                refreshInterval = config.oauthTokenRefreshInterval,
                onRefreshSuccess = config.onTokenRefreshSuccess.getOrElse(Applicative[F].unit),
                onRefreshError = config.onTokenRefreshError,
                retryDelay = config.oauthTokenFailureRetryDelay,
                retryNextDelay = config.oauthTokenFailureRetryNextDelay,
                retryMaxAttempts = config.oauthTokenFailureRetryMaxAttempts,
                onRetriesExhausted = config.onTokenRetriesExhausted,
              )
            } yield TokenProvider.instance(accessTokenRefEffect.value)
          )
    } yield new DefaultHttpPublisher[F, A](
      baseApiUrl = createBaseApiUri(projectId, topic, config),
      client = httpClient,
      requestAuthorizer = RequestAuthorizer.tokenProvider(tokenProvider),
    )

  def createBaseApiUri[F[_]](
    projectId: Model.ProjectId,
    topic: Model.Topic,
    config: PubsubHttpProducerConfig[F]
  ): Uri =
    Uri(
      scheme = Option(if (config.port == 443) Uri.Scheme.https else Uri.Scheme.http),
      authority = Option(Uri.Authority(host = RegName(config.host), port = Option(config.port))),
      path = Uri.Path.unsafeFromString(s"/v1/projects/${projectId.value}/topics/${topic.value}")
    )

  case class Message(
    data: String,
    messageId: String,
    attributes: Map[String, String]
  )

  case class MessageBundle[G[_]](
    messages: G[Message]
  )

  case class MessageIds(
    messageIds: List[MessageId]
  )

  implicit final def foldableMessagesCodec[G[_]](implicit G: Foldable[G]): JsonValueCodec[G[Message]] =
    new JsonValueCodec[G[Message]] {
      override def decodeValue(in: JsonReader, default: G[Message]): G[Message] = ???

      override def encodeValue(x: G[Message], out: JsonWriter): Unit = {
        out.writeArrayStart()
        x.foreach(MessageCodec.encodeValue(_, out))
        out.writeArrayEnd()
      }

      override def nullValue: G[Message] = ???
    }

  implicit final val MessageCodec: JsonValueCodec[Message] =
    JsonCodecMaker.make[Message](CodecMakerConfig)

  implicit final def messageBundleCodec[G[_]](implicit
    Codec: JsonValueCodec[G[Message]]
  ): JsonValueCodec[MessageBundle[G]] =
    new JsonValueCodec[MessageBundle[G]] {
      override def decodeValue(in: JsonReader, default: MessageBundle[G]): MessageBundle[G] = ???

      override def encodeValue(x: MessageBundle[G], out: JsonWriter): Unit = {
        out.writeObjectStart();
        out.writeNonEscapedAsciiKey("messages");
        Codec.encodeValue(x.messages, out);
        out.writeObjectEnd();
      }

      override def nullValue: MessageBundle[G] = ???
    }

  implicit final val MessageIdsCodec: JsonValueCodec[MessageIds] =
    JsonCodecMaker.make[MessageIds](CodecMakerConfig)

  case class FailedRequestToPubsub(status: Status, response: String)
      extends Throwable(s"Failed request to pubsub. Response was: $response")
}
