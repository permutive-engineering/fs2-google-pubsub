/*
 * Copyright 2019-2024 Permutive Ltd. <https://permutive.com>
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

package fs2.pubsub.grpc

import cats.effect.Temporal

import fs2.pubsub.MessageEncoder
import fs2.pubsub.dsl.client.PubSubClientStep
import fs2.pubsub.dsl.publisher.PubSubPublisherStep
import fs2.pubsub.dsl.subscriber.PubSubSubscriberStep

object GrpcConstructors {

  trait Publisher {

    @deprecated("gRPC implementation is not available on Scala 2.12", "1.1.0")
    def grpc[F[_]: Temporal, A: MessageEncoder]: PubSubPublisherStep[F, A] = ???

  }

  trait Subscriber {

    @deprecated("gRPC implementation is not available on Scala 2.12", "1.1.0")
    def grpc[F[_]: Temporal]: PubSubSubscriberStep[F] = ???

  }

  trait Client {

    @deprecated("gRPC implementation is not available on Scala 2.12", "1.1.0")
    def grpc[F[_]: Temporal]: PubSubClientStep[F] = ???

  }

}
