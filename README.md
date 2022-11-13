# typedcurrency
Gradle based generated code for fixed decimal currency computation using rounding as well as spring boot autoconfig for using in Jackson DTOs

`CurrencyUsdS3Gross` for example is a currency value in USD with 3 decimals, and gross (after VAT was added).

This enables typesafe management of the value in question already having had applied VAT or not, and to guarantee rounding when transforming the value.
