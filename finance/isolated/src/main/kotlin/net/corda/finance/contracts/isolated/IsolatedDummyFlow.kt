package net.corda.finance.contracts.isolated

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.rootMessage
import net.corda.core.utilities.unwrap

/**
 * Just sends a dummy state to the other side: used for testing whether attachments with code in them are being
 * loaded or blocked.
 */
class IsolatedDummyFlow {
    @StartableByRPC
    @InitiatingFlow
    class Initiator(private val toWhom: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val tx = AnotherDummyContract().generateInitial(
                    serviceHub.myInfo.legalIdentities.first().ref(0),
                    1234,
                    serviceHub.networkMapCache.notaryIdentities.first()
            )
            val stx = serviceHub.signInitialTransaction(tx)
            val session = initiateFlow(toWhom)
            subFlow(SendTransactionFlow(session, stx))
            session.receive<String>().unwrap {require(it == "OK") { "Not OK: $it"} }
        }
    }

    @InitiatedBy(Initiator::class)
    class Acceptor(private val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val stx = try {
                subFlow(ReceiveTransactionFlow(session, checkSufficientSignatures = false))
            } catch (e: Exception) {
                throw FlowException(e.rootMessage)
            }
            stx.verify(serviceHub)
            session.send("OK")
        }
    }
}
