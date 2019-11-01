package com.github.opticyclic.corda.demo.linearpointers.states

import com.github.opticyclic.corda.demo.linearpointers.contracts.MBSContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

/**
 * A deliberately basic model of an MBS.
 *
 * This is simply the [issuer], a list of [mortgages] that make up the pool and the current [price].
 * There is no function to calculate the [price].
 * For simplicity, we assume that whenever the [mortgages] are updated, the price is calculated and set correctly.
 */
@BelongsToContract(MBSContract::class)
data class MBSState(val issuer: Party,
                    val mortgages: List<LinearPointer<MortgageState>>,
                    val price: Double,
                    override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState {
    constructor(issuer: Party) : this(issuer, emptyList(), 0.0)

    override val participants: List<AbstractParty> get() = listOf(issuer)
}
