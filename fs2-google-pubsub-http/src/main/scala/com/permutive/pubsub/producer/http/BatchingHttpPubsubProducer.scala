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

package com.permutive.pubsub.producer.http

import cats.syntax.all._
import cats.effect.kernel.{Async, Resource}
import com.permutive.pubsub.producer.encoder.MessageEncoder
import com.permutive.pubsub.producer.http.internal.{BatchingHttpPublisher, DefaultHttpPublisher}
import com.permutive.pubsub.producer.{AsyncPubsubProducer, Model}
import org.typelevel.log4cats.Logger
import org.http4s.client.Client
import com.permutive.pubsub.http.oauth.TokenProvider
import com.permutive.pubsub.http.oauth.DefaultTokenProvider

object BatchingHttpPubsubProducer {

  /**
    * Create an HTTP PubSub producer which produces in batches.
    *
    * @param projectId                google cloud project id
    * @param topic                    the topic to produce to
    * @param tokenProvider            token provider representing a google service account, if not specified then the GCP
    *                                 metadata endpoint is used to retrieve the `default` service account access token
    *
    * See the following for documentation on GCP metadata endpoint and service accounts:
    *  - https://cloud.google.com/compute/docs/storing-retrieving-metadata
    *  - https://cloud.google.com/compute/docs/metadata/default-metadata-values
    *  - https://cloud.google.com/compute/docs/access/create-enable-service-accounts-for-instances
    */
  def resource[F[_]: Async: Logger, A: MessageEncoder](
    projectId: Model.ProjectId,
    topic: Model.Topic,
    googleServiceAccountPath: Option[String],
    config: PubsubHttpProducerConfig[F],
    batchingConfig: BatchingHttpProducerConfig,
    httpClient: Client[F]
  ): Resource[F, AsyncPubsubProducer[F, A]] =
    for {
      tokenProvider <- Resource.eval(googleServiceAccountPath.traverse(p => DefaultTokenProvider.google(p, httpClient)))
      publisher <- DefaultHttpPublisher.resource(
        projectId = projectId,
        topic = topic,
        maybeTokenProvider = tokenProvider,
        config = config,
        httpClient = httpClient
      )
      batching <- BatchingHttpPublisher.resource(
        publisher = publisher,
        config = batchingConfig
      )
    } yield batching

  /**
    * Create an HTTP PubSub producer which produces in batches.
    *
    * @param projectId                google cloud project id
    * @param topic                    the topic to produce to
    * @param tokenProvider            token provider representing a google service account, if not specified then the GCP
    *                                 metadata endpoint is used to retrieve the `default` service account access token
    *
    * See the following for documentation on GCP metadata endpoint and service accounts:
    *  - https://cloud.google.com/compute/docs/storing-retrieving-metadata
    *  - https://cloud.google.com/compute/docs/metadata/default-metadata-values
    *  - https://cloud.google.com/compute/docs/access/create-enable-service-accounts-for-instances
    */
  def resourceWithProvider[F[_]: Async: Logger, A: MessageEncoder](
    projectId: Model.ProjectId,
    topic: Model.Topic,
    tokenProvider: Option[TokenProvider[F]],
    config: PubsubHttpProducerConfig[F],
    batchingConfig: BatchingHttpProducerConfig,
    httpClient: Client[F]
  ): Resource[F, AsyncPubsubProducer[F, A]] =
    for {
      publisher <- DefaultHttpPublisher.resource(
        projectId = projectId,
        topic = topic,
        maybeTokenProvider = tokenProvider,
        config = config,
        httpClient = httpClient
      )
      batching <- BatchingHttpPublisher.resource(
        publisher = publisher,
        config = batchingConfig
      )
    } yield batching
}
