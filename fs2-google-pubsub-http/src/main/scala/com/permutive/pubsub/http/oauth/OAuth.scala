package com.permutive.pubsub.http.oauth

import java.time.Instant

import scala.concurrent.duration.FiniteDuration

trait OAuth[F[_]] {

  /**
    * Based on https://developers.google.com/identity/protocols/OAuth2ServiceAccount
    * @param iss The email address of the service account.
    * @param scope A space-delimited list of the permissions that the application requests.
    * @param exp The expiration time of the assertion, specified as milliseconds since 00:00:00 UTC, January 1, 1970.
    * @param iat The time the assertion was issued, specified as milliseconds since 00:00:00 UTC, January 1, 1970.
    */
  def authenticate(
    iss: String,
    scope: String,
    exp: Instant = Instant.now().plusMillis(maxDuration.toMillis),
    iat: Instant = Instant.now(),
  ): F[Option[AccessToken]]

  def maxDuration: FiniteDuration
}
