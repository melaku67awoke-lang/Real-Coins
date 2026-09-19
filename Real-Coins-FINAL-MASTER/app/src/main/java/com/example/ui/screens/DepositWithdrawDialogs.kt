package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// Constants specified by user
const val BEP20_DEPOSIT_ADDRESS = "0x8e54105bed3243e1ca44a0cccc6b62cf2bff9df4"
const val REAL_COIN_USD_VALUE = 0.0027
const val USD_TO_ETB = 186.0
const val MIN_DEPOSIT_USD = 25.0
const val MIN_WITHDRAWAL_USD = 50.0
const val MIN_DEPOSIT_REAL_COIN = MIN_DEPOSIT_USD / REAL_COIN_USD_VALUE
const val MIN_WITHDRAWAL_REAL_COIN = MIN_WITHDRAWAL_USD / REAL_COIN_USD_VALUE

@Composable
fun WarningNoticeBox(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFFFF3CD),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFEEBA)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = Color(0xFF856404),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                fontSize = 12.sp,
                color = Color(0xFF856404),
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun DepositDialog(
    realCoinUsdPrice: Double = REAL_COIN_USD_VALUE,
    usdToEtbRate: Double = USD_TO_ETB,
    onDismiss: () -> Unit,
    onSubmitDeposit: (txHash: String, amountRealCoin: Double) -> Unit
) {
    val context = LocalContext.current
    var txHash by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    val depositUsd = amountText.toDoubleOrNull() ?: 0.0
    val depositReal = if (realCoinUsdPrice > 0.0) depositUsd / realCoinUsdPrice else 0.0
    val bonusReal = depositReal * 0.10
    val depositMeetsMinimum = depositUsd >= MIN_DEPOSIT_USD

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Deposit RealCoin",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Mandatory BEP 20 Notice
                WarningNoticeBox(
                    message = "Warning Notice: Users must use BEP-20 (BNB Smart Chain) address only for both Deposit and withdrawals. Deposits sent through other networks will be permanently lost."
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Network Tag & Contract Details
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Network", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text("BSC BNB Smart Chain (BEP20)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Contract Info", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text("***97955", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Deposit Address Display (from screenshot)
                Text(
                    text = "Deposit Address (BEP20)",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.Start),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = BEP20_DEPOSIT_ADDRESS,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("BEP20 Deposit Address", BEP20_DEPOSIT_ADDRESS))
                            Toast.makeText(context, "BEP-20 Address copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("copy_deposit_address_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Address",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Deposit Amount (USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("deposit_amount_input"),
                    isError = amountText.isNotBlank() && !depositMeetsMinimum,
                    supportingText = {
                        Text("You receive: ${"%,.2f".format(depositReal)} RC • 10% bonus: ${"%,.2f".format(bonusReal)} RC • Total bonus is promotional and locked. Minimum $25 USD")
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = txHash,
                    onValueChange = { txHash = it },
                    label = { Text("Transaction Hash (TxID)") },
                    placeholder = { Text("0x...") },
                    modifier = Modifier.fillMaxWidth().testTag("deposit_txhash_input")
                )

                Text(
                    "Today's value: $${"%.4f".format(realCoinUsdPrice)} USD/REAL • ${"%.2f".format(realCoinUsdPrice * usdToEtbRate)} ETB/REAL",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull() ?: 0.0
                            if (amt > 0 && depositMeetsMinimum && txHash.isNotBlank()) {
                                onSubmitDeposit(txHash.trim(), depositReal)
                                onDismiss()
                            } else {
                                Toast.makeText(context, "Deposit must be at least $25 USD and include a TxHash", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("submit_deposit_button")
                    ) {
                        Text("Confirm Deposit")
                    }
                }
            }
        }
    }
}

@Composable
fun WithdrawDialog(
    userBalanceRealCoin: Double,
    realCoinUsdPrice: Double = REAL_COIN_USD_VALUE,
    usdToEtbRate: Double = USD_TO_ETB,
    onDismiss: () -> Unit,
    onSubmitWithdrawal: (bep20Address: String, amountRealCoin: Double) -> Unit
) {
    val context = LocalContext.current
    var bep20Address by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }

    val amountRealCoin = amountText.toDoubleOrNull() ?: 0.0
    val withdrawalUsdValue = amountRealCoin * realCoinUsdPrice
    val minRealCoinRequired = MIN_WITHDRAWAL_USD / realCoinUsdPrice

    val isAddressValid = bep20Address.trim().matches(Regex("^0x[a-fA-F0-9]{40}$"))
    val meetsMinimumUsd = withdrawalUsdValue >= MIN_WITHDRAWAL_USD
    val hasSufficientBalance = amountRealCoin <= userBalanceRealCoin && amountRealCoin > 0

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Withdraw RealCoin",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Mandatory BEP 20 Notice
                WarningNoticeBox(
                    message = "Warning Notice: Users must use BEP-20 (BNB Smart Chain) address only for both Deposit and withdrawals. Withdrawal to any non-BEP-20 address will fail."
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Price & Minimum USD Rule Card
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Today's Real Coin value: \$${"%.4f".format(realCoinUsdPrice)} USD",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Minimum withdrawal: $50.00 USD (~${"%,.2f".format(minRealCoinRequired)} RC)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Available Balance: ${"%,.2f".format(userBalanceRealCoin)} RC ($${"%.2f".format(userBalanceRealCoin * realCoinUsdPrice)} USD)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = bep20Address,
                    onValueChange = { bep20Address = it },
                    label = { Text("BEP-20 Receiving Address (BSC)") },
                    placeholder = { Text("0x...") },
                    modifier = Modifier.fillMaxWidth().testTag("withdraw_address_input"),
                    isError = bep20Address.isNotBlank() && !isAddressValid,
                    supportingText = {
                        if (bep20Address.isNotBlank() && !isAddressValid) {
                            Text("Must be a valid 42-character BEP-20 address starting with 0x", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Network: BNB Smart Chain (BEP-20)")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Withdraw Amount (RC)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("withdraw_amount_input"),
                    isError = amountText.isNotBlank() && (!meetsMinimumUsd || !hasSufficientBalance),
                    supportingText = {
                        if (amountText.isNotBlank()) {
                            if (!hasSufficientBalance) {
                                Text("Insufficient RealCoin balance", color = MaterialTheme.colorScheme.error)
                            } else if (!meetsMinimumUsd) {
                                Text(
                                    "Current value: $${"%.2f".format(withdrawalUsdValue)} USD. Must be at least $50.00 USD (~${"%,.2f".format(minRealCoinRequired)} RC). Withdraw request is blocked.",
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    "Value: $${"%.2f".format(withdrawalUsdValue)} USD (Eligible for withdrawal)",
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (!meetsMinimumUsd) {
                                Toast.makeText(
                                    context,
                                    "Withdrawal blocked: Minimum value is $50.00 USD (~${"%,.0f".format(minRealCoinRequired)} Real Coin). Current value is only $${"%.2f".format(withdrawalUsdValue)} USD.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else if (!isAddressValid) {
                                Toast.makeText(context, "Please enter a valid BEP-20 address", Toast.LENGTH_SHORT).show()
                            } else if (!hasSufficientBalance) {
                                Toast.makeText(context, "Insufficient balance", Toast.LENGTH_SHORT).show()
                            } else {
                                onSubmitWithdrawal(bep20Address.trim(), amountRealCoin)
                                onDismiss()
                            }
                        },
                        enabled = meetsMinimumUsd && isAddressValid && hasSufficientBalance,
                        modifier = Modifier.testTag("submit_withdraw_button")
                    ) {
                        Text("Submit Withdrawal")
                    }
                }
            }
        }
    }
}
