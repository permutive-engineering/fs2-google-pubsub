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

package com.permutive.pubsub.http.util

import cats.MonadError
import cats.effect.kernel.{Ref, Resource, Temporal}
import cats.effect.syntax.all._
import cats.syntax.all._
import fs2.Stream

import scala.concurrent.duration.FiniteDuration

/** Represents a value of type `A` with effects in `F` which is refreshed.
  *
  * Refreshing can be cancelled by evaluating `cancelToken`.
  *
  * Implementation is backed by a `cats-effect` `Ref` so evaluating the value is fast.
  */
final class RefreshableEffect[F[_], A] private (val value: F[A], val cancelToken: F[Unit])

object RefreshableEffect {

  /** Create a refreshable effect which exposes the result of `refresh`, retries
    * if refreshing the value fails.
    *
    * @param refreshInterval    how frequently to refresh the value
    * @param onRefreshError     what to do if refreshing the value fails, error is always rethrown
    * @param onRefreshSuccess   what to do when the value is successfully refresh, errors are ignored
    * @param retryDelay         duration of delay before the first retry
    * @param retryNextDelay     what value to delay before the next retry
    * @param retryMaxAttempts   how many attempts to make before failing with last error
    * @param onRetriesExhausted what to do if retrying to refresh the value fails, up to user handle failing their service
    */
  def createRetryResource[F[_]: Temporal, A](
    refresh: F[A],
    refreshInterval: FiniteDuration,
    onRefreshSuccess: F[Unit],
    onRefreshError: PartialFunction[Throwable, F[Unit]],
    retryDelay: FiniteDuration,
    retryNextDelay: FiniteDuration => FiniteDuration,
    retryMaxAttempts: Int,
    onRetriesExhausted: PartialFunction[Throwable, F[Unit]],
  ): Resource[F, RefreshableEffect[F, A]] = {
    val updateRef: Ref[F, A] => F[Unit] =
      ref =>
        retry(
          updateUnhandled(refresh, ref, onRefreshSuccess).onError(onRefreshError),
          retryDelay,
          retryNextDelay,
          retryMaxAttempts,
        ).onError(onRetriesExhausted).attempt.void // Swallow errors entirely, retry will loop around again

    Resource.make(createAndSchedule(refresh, refreshInterval, updateRef))(_.cancelToken)
  }

  private def createAndSchedule[F[_]: Temporal, A](
    refresh: F[A],
    refreshInterval: FiniteDuration,
    updateRef: Ref[F, A] => F[Unit],
  ): F[RefreshableEffect[F, A]] =
    for {
      initial <- refresh
      ref     <- Ref.of(initial)
      fiber   <- scheduleRefresh(updateRef(ref), refreshInterval).start
    } yield new RefreshableEffect[F, A](ref.get, fiber.cancel)

  private def scheduleRefresh[F[_]: Temporal, A](
    refreshEffect: F[Unit],
    refreshInterval: FiniteDuration,
  ): F[Unit] =
    Stream
      .fixedRate(refreshInterval) // Same frequency regardless of time to evaluate refresh
      .evalMap(_ => refreshEffect)
      .compile
      .drain

  private def updateUnhandled[F[_], A](refresh: F[A], ref: Ref[F, A], onRefreshSuccess: F[Unit])(implicit
    ME: MonadError[F, Throwable]
  ): F[Unit] =
    for {
      refreshed <- refresh
      _         <- ref.set(refreshed)
      _         <- onRefreshSuccess.attempt // Ignore exceptions in success callback
    } yield ()

  private def retry[F[_]: Temporal, A](
    refreshEffect: F[Unit],
    retryDelay: FiniteDuration,
    retryNextDelay: FiniteDuration => FiniteDuration,
    retryMaxAttempts: Int,
  ): F[Unit] =
    if (retryMaxAttempts < 1)
      refreshEffect
    else
      Stream
        .retry(
          refreshEffect,
          retryDelay,
          retryNextDelay,
          retryMaxAttempts,
        )
        .compile
        .lastOrError

}
