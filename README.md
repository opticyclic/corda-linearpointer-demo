# Corda Linear Pointer Demo

This repo demonstrates the use of Linear Pointers in Corda.

It uses a _very_ simplistic model of an unsecured MBS (Mortgage Backed Security).

* LenderA creates 3 mortgages
* LenderB creates 2 mortgages
* Issuer creates an empty MBS
* LenderA sells his mortgages to the Issuer
* LenderB sells his mortgages to the Issuer
* Issuer adds the mortgages to the MBS
* Issuer sells the MBS to an Investor

The mortgages are added to the MBS using Linear Pointers.

This means that the MBS state can be updated without modifying the mortgage states.

The Investor will be able to see the total valuation but won't be able to see the detail of the mortgages backing it as they won't be able to resolve the Linear Pointers.  
