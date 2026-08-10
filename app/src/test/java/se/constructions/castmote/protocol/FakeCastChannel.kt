package se.constructions.castmote.protocol

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import se.constructions.castmote.proto.CastMessage

/** In-memory CastChannel for tests: records sends, lets tests inject incoming messages. */
class FakeCastChannel : CastChannel {
    val sent = mutableListOf<CastMessage>()
    private val _incoming = MutableSharedFlow<CastMessage>(extraBufferCapacity = 64)
    override val incoming = _incoming.asSharedFlow()
    var closed = false

    override suspend fun send(message: CastMessage) { sent.add(message) }

    suspend fun inject(message: CastMessage) { _incoming.emit(message) }

    override fun close() { closed = true }
}
