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

package com.permutive.pubsub.consumer.http.internal

import cats.Applicative
import cats.effect.kernel._
import cats.syntax.all._
import com.github.plokhotnyuk.jsoniter_scala.core.{readFromArray, writeToArray, JsonValueCodec}
import com.github.plokhotnyuk.jsoniter_scala.macros.{CodecMakerConfig, JsonCodecMaker}
import com.permutive.pubsub.consumer.Model.{ProjectId, Subscription}
import com.permutive.pubsub.consumer.http.PubsubHttpConsumerConfig
import com.permutive.pubsub.consumer.http.internal.Model._
import com.permutive.pubsub.http.oauth._
import com.permutive.pubsub.http.util.RefreshableEffect
import org.http4s.Method._
import org.http4s.Uri._
import org.http4s._
import org.http4s.client._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers._
import org.typelevel.log4cats.Logger

import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NoStackTrace

private[internal] class HttpPubsubReader[F[_]: Async: Logger] private (
  baseApiUrl: Uri,
  client: Client[F],
  requestAuthorizer: RequestAuthorizer[F],
  returnImmediately: Boolean,
  maxMessages: Int
) extends PubsubReader[F] {
  object dsl extends Http4sClientDsl[F]

  import HttpPubsubReader._
  import dsl._

  private def appendToUrl(s: String): Uri =
    Uri.unsafeFromString(s"${baseApiUrl.renderString}:$s")

  final private[this] val pullEndpoint           = appendToUrl("pull")
  final private[this] val acknowledgeEndpoint    = appendToUrl("acknowledge")
  final private[this] val modifyDeadlineEndpoint = appendToUrl("modifyAckDeadline")

  final override val read: F[PullResponse] = {
    for {
      json <- Sync[F].delay(
        writeToArray(
          PullRequest(
            returnImmediately = returnImmediately,
            maxMessages = maxMessages
          )
        )
      )
      req = POST(json, pullEndpoint, `Content-Type`(MediaType.application.json))
      authedReq <- requestAuthorizer.authorize(req)
      resp      <- client.expectOr[Array[Byte]](authedReq)(onError)
      decoded <- Sync[F].delay(readFromArray[PullResponse](resp)).onError { case _ =>
        Logger[F].error(s"Pull response from PubSub was invalid. Body: ${new String(resp)}")
      }
    } yield decoded
  }

  final override def ack(ackIds: List[AckId]): F[Unit] =
    for {
      json <- Sync[F].delay(
        writeToArray(
          AckRequest(
            ackIds = ackIds
          )
        )
      )
      req = POST(json, acknowledgeEndpoint, `Content-Type`(MediaType.application.json))
      authedReq <- requestAuthorizer.authorize(req)
      _         <- client.expectOr[Array[Byte]](authedReq)(onError)
    } yield ()

  final override def nack(ackIds: List[AckId]): F[Unit] =
    modifyDeadline(ackIds, 0.seconds)

  final override def modifyDeadline(ackId: List[AckId], by: FiniteDuration): F[Unit] =
    for {
      json <- Sync[F].delay(
        writeToArray(
          ModifyAckDeadlineRequest(
            ackIds = ackId,
            ackDeadlineSeconds = by.toSeconds,
          )
        )
      )
      req = POST(json, modifyDeadlineEndpoint, `Content-Type`(MediaType.application.json))
      authedReq <- requestAuthorizer.authorize(req)
      _         <- client.expectOr[Array[Byte]](authedReq)(onError)
    } yield ()

  @inline
  final private def onError(resp: Response[F]): F[Throwable] =
    resp
      .as[Array[Byte]]
      .map(arr =>
        Try(readFromArray[PubSubErrorResponse](arr))
          .fold(_ => PubSubError.UnparseableBody(new String(arr)), PubSubError.fromResponse)
      )
}

private[internal] object HttpPubsubReader {
  def resource[F[_]: Async: Logger](
    projectId: ProjectId,
    subscription: Subscription,
    serviceAccountPath: Option[String],
    config: PubsubHttpConsumerConfig[F],
    httpClient: Client[F],
  ): Resource[F, PubsubReader[F]] =
    for {
      maybeTokenProvider <- Resource.eval(serviceAccountPath.traverse(p => DefaultTokenProvider.google(p, httpClient)))
      reader             <- resourceWithProvider(projectId, subscription, maybeTokenProvider, config, httpClient)
    } yield reader

  def resourceWithProvider[F[_]: Async: Logger](
    projectId: ProjectId,
    subscription: Subscription,
    maybeTokenProvider: Option[TokenProvider[F]],
    config: PubsubHttpConsumerConfig[F],
    httpClient: Client[F],
  ): Resource[F, PubsubReader[F]] =
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
    } yield new HttpPubsubReader[F](
      baseApiUrl = createBaseApi(config, ProjectNameSubscription.of(projectId, subscription)),
      client = httpClient,
      requestAuthorizer = RequestAuthorizer.tokenProvider(tokenProvider),
      returnImmediately = config.readReturnImmediately,
      maxMessages = config.readMaxMessages
    )

  def createBaseApi[F[_]](config: PubsubHttpConsumerConfig[F], projectNameSubscription: ProjectNameSubscription): Uri =
    Uri(
      scheme = Option(if (config.port == 443) Uri.Scheme.https else Uri.Scheme.http),
      authority = Option(Uri.Authority(host = RegName(config.host), port = Option(config.port))),
      path = Uri.Path.unsafeFromString(s"/v1/${projectNameSubscription.value}")
    )

  sealed abstract class PubSubError(msg: String)
      extends Throwable(s"Failed request to PubSub. Underlying message: $msg")
      with NoStackTrace

  object PubSubError {
    case object NoAckIds                          extends PubSubError("No ack ids specified")
    case class Unknown(body: PubSubErrorResponse) extends PubSubError(body.toString)
    case class UnparseableBody(body: String)      extends PubSubError(s"Body could not be parsed to error response: $body")

    def fromResponse(response: PubSubErrorResponse): PubSubError =
      response.error.message match {
        case "No ack ids specified." => NoAckIds
        case _                       => Unknown(response)
      }
  }

  case class PubSubErrorMessage(message: String, status: String, code: Int)
  case class PubSubErrorResponse(error: PubSubErrorMessage)

  object PubSubErrorResponse {
    implicit final val Codec: JsonValueCodec[PubSubErrorResponse] =
      JsonCodecMaker.make[PubSubErrorResponse](CodecMakerConfig)
  }
}
