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

package com.permutive.pubsub.consumer.decoder

import cats.Functor
import cats.syntax.all._

import java.nio.charset.StandardCharsets

trait MessageDecoder[A] {

  def decode(message: Array[Byte]): Either[Throwable, A]

  def map[B](f: A => B): MessageDecoder[B] = MessageDecoder.functor.map(this)(f)

  def emap[B](f: A => Either[Throwable, B]): MessageDecoder[B] = this.decode(_).flatMap(f)

}

object MessageDecoder {

  def apply[A: MessageDecoder]: MessageDecoder[A] = implicitly

  implicit val string: MessageDecoder[String] = bytes => Either.catchNonFatal(new String(bytes, StandardCharsets.UTF_8))

  implicit val functor: Functor[MessageDecoder] = new Functor[MessageDecoder] {

    override def map[A, B](fa: MessageDecoder[A])(f: A => B): MessageDecoder[B] =
      (message: Array[Byte]) => fa.decode(message).map(f)

  }

}
