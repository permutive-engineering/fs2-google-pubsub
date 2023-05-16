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

package com.permutive.pubsub.producer.encoder

import cats.Contravariant
import cats.syntax.all._
import java.nio.charset.StandardCharsets

trait MessageEncoder[A] {

  def encode(a: A): Either[Throwable, Array[Byte]]

  def contramap[B](f: B => A): MessageEncoder[B] = b => this.encode(f(b))

}

object MessageEncoder {

  def apply[A: MessageEncoder]: MessageEncoder[A] = implicitly

  implicit val string: MessageEncoder[String] = _.getBytes(StandardCharsets.UTF_8).asRight

  implicit val MessageEncoderContravariant: Contravariant[MessageEncoder] = new Contravariant[MessageEncoder] {

    override def contramap[A, B](fa: MessageEncoder[A])(f: B => A): MessageEncoder[B] = b => fa.encode(f(b))

  }

}
