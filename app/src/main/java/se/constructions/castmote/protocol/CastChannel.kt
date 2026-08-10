package se.constructions.castmote.protocol

import kotlinx.coroutines.flow.Flow
import se.constructions.castmote.proto.CastMessage

/** A bidirectional CASTV2 message channel to a single device. */
interface CastChannel {
    /** Inbound decoded messages. */
    val incoming: Flow<CastMessage>

    /** Send one message (suspends until written). */
    suspend fun send(message: CastMessage)

    /** Close the underlying transport. */
    fun close()
}
