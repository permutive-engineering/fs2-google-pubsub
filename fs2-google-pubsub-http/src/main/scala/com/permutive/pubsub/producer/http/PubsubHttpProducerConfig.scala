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

import scala.concurrent.duration._

/** Configuration for the PubSub HTTP producer.
  *
  * Note: It is up to the user to raise exceptions and terminate the lifecyle of services if this is desired when
  * retries are exhausted. Token refreshing runs on a background fiber so raising exceptions in
  * `onTokenRetriesExhausted` will have _no_ effect to the main fibers, in fact errors are swallowed entirely.
  *
  * @param host                              host of PubSub
  * @param port                              port of PubSub
  * @param isEmulator                        whether the target PubSub is an emulator or not
  * @param oauthTokenRefreshInterval         how often to refresh the Google OAuth token
  * @param onTokenRefreshSuccess             optional callback to execute when refreshing the token succeeds, errors are ignored
  * @param onTokenRefreshError               callback to execute if refreshing the token fails during retries, errors rethrown and retried
  * @param oauthTokenFailureRetryDelay       initial delay for retrying OAuth token retrieval
  * @param oauthTokenFailureRetryNextDelay   next delay for retrying OAuth token retrieval
  * @param oauthTokenFailureRetryMaxAttempts how many times to attempt; will raise the last error once reached
  * @param onTokenRetriesExhausted           callback to execute if refreshing the token exhausts retries.
  *                                          See note above on exceptions, up to the *user* to raise exceptions in their
  *                                          service using this callback if required.
  */
case class PubsubHttpProducerConfig[F[_]](
  host: String = "pubsub.googleapis.com",
  port: Int = 443,
  isEmulator: Boolean = false,
  oauthTokenRefreshInterval: FiniteDuration = 30.minutes,
  onTokenRefreshSuccess: Option[F[Unit]] = None,
  onTokenRefreshError: PartialFunction[Throwable, F[Unit]] = PartialFunction.empty,
  oauthTokenFailureRetryDelay: FiniteDuration = 0.millis,
  oauthTokenFailureRetryNextDelay: FiniteDuration => FiniteDuration = _ => 5.minutes,
  oauthTokenFailureRetryMaxAttempts: Int = Int.MaxValue,
  onTokenRetriesExhausted: PartialFunction[Throwable, F[Unit]] = PartialFunction.empty,
)
