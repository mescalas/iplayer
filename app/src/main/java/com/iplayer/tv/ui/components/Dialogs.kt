package com.iplayer.tv.ui.components

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.iplayer.tv.ui.theme.C
import com.iplayer.tv.ui.theme.T
import kotlinx.coroutines.delay

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    hint: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(600.dp).clip(RowShape).background(Color(0xFF111113)).border(1.dp, Color(0x1FFFFFFF), RowShape).padding(28.dp)
        ) {
            Text(title.uppercase(java.util.Locale.FRENCH), style = T.Label.copy(fontSize = 13.sp))
            if (hint != null) {
                Spacer(Modifier.height(4.dp))
                Text(hint, style = T.Subhead, color = C.Text2)
            }
            Spacer(Modifier.height(18.dp))
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = T.Title3.copy(color = C.Text),
                cursorBrush = SolidColor(C.Accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone(value.text.trim()) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .clip(RowShape)
                    .background(Color(0x14FFFFFF))
                    .border(1.dp, Color(0x33FFFFFF), RowShape)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                PillButton("Annuler", onClick = onDismiss)
                PillButton("Valider", onClick = { onDone(value.text.trim()) }, primary = true)
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(120)
        focus.tryFocus()
        keyboard?.show()
    }
}

data class DialogAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

@Composable
fun ActionDialog(
    title: String,
    message: String? = null,
    actions: List<DialogAction>,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(460.dp).clip(RowShape).background(Color(0xFF111113)).border(1.dp, Color(0x1FFFFFFF), RowShape).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title.uppercase(java.util.Locale.FRENCH), style = T.Label.copy(fontSize = 13.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (message != null) {
                Spacer(Modifier.height(6.dp))
                Text(message, style = T.Subhead, color = C.Text2)
            }
            Spacer(Modifier.height(18.dp))
            actions.forEachIndexed { i, a ->
                FocusSurface(
                    onClick = { a.onClick() },
                    modifier = Modifier.fillMaxWidth().height(50.dp).then(if (i == 0) Modifier.focusRequester(focus) else Modifier),
                    shape = RoundedCornerShape(25.dp),
                    color = Color(0x14FFFFFF),
                    contentColor = if (a.destructive) C.Red else C.Text,
                    focusedContentColor = if (a.destructive) Color(0xFFD70015) else C.OnFocus,
                    focusedScale = 1.03f,
                    contentAlignment = Alignment.Center,
                ) {
                    Text(a.label.uppercase(java.util.Locale.FRENCH), style = T.Label.copy(fontSize = 12.sp))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(80)
        focus.tryFocus()
    }
}
