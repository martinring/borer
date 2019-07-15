/*
 * Copyright (c) 2019 Mathias Doenitz
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package io.bullet.borer

import java.nio.ByteBuffer

object ByteBufferCborSpec extends AbstractCborSpec {

  def encode[T: Encoder](value: T): String =
    toHexString(ByteAccess.ForByteBuffer.toByteArray(Cbor.encode(value).toByteBuffer))

  def decode[T: Decoder](encoded: String): T =
    Cbor.decode(ByteBuffer.wrap(hexBytes(encoded))).to[T].value
}