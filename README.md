# Corda Linear Pointer Demo

This repo demonstrates the use of Linear Pointers in Corda.

It uses a _very_ simplistic model of an unsecured MBS (Mortgage Backed Security).

## What Is An MBS + How Does It Work?

Homeowners can get a mortgage from a bank to buy their house. The bank gives them a lump sum, and the owners pay the loan back in monthly payments.

The bank sells the rights to these payments to an Investment bank/issuer.
The banks charge a fee and also make some profit from this since they are still servicing the homeowner.

This frees up the capital (that the bank had previously given to the homeowner to buy the house) to invest elsewhere.

The Investment bank will create a Special Purpose Entity (a new legal company) to hold all these loans.

This SPE now receives all the monthly payments from the mortgages (and the principal at maturity), which means the SPE now has assets and thus value.

Shares are issued in this SPE and sold to Investors -> **this is the MBS**.

If homeowners default on their mortgage then the value of the SPE will go down, and the return on the SPE shares will go down too.

## What Part Of That Does This Demo Do?

The purpose of the code is to demonstrate Linear Pointers, so it skims over several parts of the business case such as creating the SPE, issuing shares and even the valuation of the MBS.

* Bank1 creates 3 mortgages
* Bank2 creates 2 mortgages
* Bank1 sells their mortgages to the Investment Bank
* Bank2 sells their mortgages to the Investment Bank
* Investment Bank creates an empty MBS
* Investment Bank adds the mortgages to the MBS using Linear Pointers
* Investment Bank sells the MBS to an Investor

The mortgages are added to the MBS using Linear Pointers which means that the MBS state can be updated without modifying
the mortgage states.

This also means that the mortgages can be updated without the MBS state being modified.

The Investor will be able to see the MBS but won't be able to see the detail of the mortgages backing it as they won't
be able to resolve the Linear Pointers.

The flow that uses the `LinearPointer` is `AddToMBSFlow`.

The `TransferMBSFlow` should demonstrate the fact that the Investor can't access the mortgages as they are
LinearPointers and haven't been transferred.

The end-to-end demonstration is in the test
class `workflows/src/test/kotlin/com/github/opticyclic/corda/demo/linearpointers/flows/EndToEndTest.kt`

The build.gradle contains all the necessary nodes, so you can run `./gradlew deployNodes` and run the flows from the
shell if you would like.
