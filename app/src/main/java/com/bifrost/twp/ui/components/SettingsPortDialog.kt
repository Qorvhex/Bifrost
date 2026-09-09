package com.bifrost.twp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bifrost.twp.R
import com.bifrost.twp.ui.theme.CardBackground
import com.bifrost.twp.ui.theme.CardStroke
import com.bifrost.twp.ui.theme.DarkBackground
import com.bifrost.twp.ui.theme.DangerRed
import com.bifrost.twp.ui.theme.NeonCyan
import com.bifrost.twp.ui.theme.TextMuted
import com.bifrost.twp.ui.theme.TextPrimary
import com.bifrost.twp.ui.theme.TextSecondary

@Composable
fun SettingsPortDialog(
    currentPort: Int,
    onDismiss: () -> Unit,
    onSavePort: (Int) -> Unit
) {
    var portText by remember { mutableStateOf(currentPort.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CardBackground)
                .border(1.dp, CardStroke, RoundedCornerShape(18.dp))
                .padding(20.dp)
        ) {
            Text(
                text = "Local SOCKS5 Port",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Configure the local port where Telegram connects (127.0.0.1).",
                color = TextMuted,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = portText,
                onValueChange = {
                    portText = it
                    error = null
                },
                label = { Text(stringResource(R.string.field_local_port), fontSize = 12.sp) },
                isError = error != null,
                supportingText = if (error != null) {
                    { Text(error!!, color = DangerRed, fontSize = 11.sp) }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = CardStroke,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedLabelColor = NeonCyan,
                    unfocusedLabelColor = TextSecondary,
                    cursorColor = NeonCyan
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Text(stringResource(R.string.action_cancel), fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = {
                        val parsed = portText.toIntOrNull()
                        if (parsed == null || parsed !in 1024..65535) {
                            error = "Port must be between 1024 and 65535"
                            return@Button
                        }
                        onSavePort(parsed)
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = DarkBackground
                    )
                ) {
                    Text(stringResource(R.string.action_save), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
