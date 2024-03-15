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

import com.permutive.pubsub.consumer.http.internal.Model.{AckId, PullResponse}

import scala.concurrent.duration.FiniteDuration

trait PubsubReader[F[_]] {
  def read: F[PullResponse]

  def ack(ackId: List[AckId]): F[Unit]

  def nack(ackId: List[AckId]): F[Unit]

  /** The new ack deadline with respect to the time this request was sent to the Pub/Sub system.
    * For example, if the value is 10, the new ack deadline will expire 10 seconds after
    * the subscriptions.modifyAckDeadline call was made. Specifying zero might immediately make the message
    * available for delivery to another subscriber client. This typically results in an increase in the rate of
    * message redeliveries (that is, duplicates). The minimum deadline you can specify is 0 seconds.
    * The maximum deadline you can specify is 600 seconds (10 minutes).
    * From: https://cloud.google.com/pubsub/docs/reference/rest/v1/projects.subscriptions/modifyAckDeadline
    */
  def modifyDeadline(ackId: List[AckId], by: FiniteDuration): F[Unit]
}

object PubsubReader {
  def apply[F[_]: PubsubReader]: PubsubReader[F] = implicitly
}
