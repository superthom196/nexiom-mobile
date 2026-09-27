package io.github.superthom196.nexiom.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.superthom196.nexiom.AppModel
import io.github.superthom196.nexiom.Screen
import io.github.superthom196.nexiom.SignInProblem
import io.github.superthom196.nexiom.resources.Res
import io.github.superthom196.nexiom.resources.hide_password
import io.github.superthom196.nexiom.resources.not_set_up_back
import io.github.superthom196.nexiom.resources.not_set_up_body
import io.github.superthom196.nexiom.resources.not_set_up_open
import io.github.superthom196.nexiom.resources.not_set_up_title
import io.github.superthom196.nexiom.resources.show_password
import io.github.superthom196.nexiom.resources.sign_in_body
import io.github.superthom196.nexiom.resources.sign_in_button
import io.github.superthom196.nexiom.resources.sign_in_household
import io.github.superthom196.nexiom.resources.sign_in_not_set_up
import io.github.superthom196.nexiom.resources.sign_in_other_box
import io.github.superthom196.nexiom.resources.sign_in_password
import io.github.superthom196.nexiom.resources.sign_in_signed_out
import io.github.superthom196.nexiom.resources.sign_in_title
import io.github.superthom196.nexiom.resources.sign_in_unreachable
import io.github.superthom196.nexiom.resources.sign_in_wrong_box
import org.jetbrains.compose.resources.stringResource

/** The household name and password; the phone signs in under its own device name. */
@Composable
fun SignInScreen(model: AppModel, screen: Screen.SignIn) {
    var household by rememberSaveable(screen.boxId) { mutableStateOf(screen.household) }
    var password by remember(screen.boxId) { mutableStateOf("") } // never into saved state
    var showPassword by remember { mutableStateOf(false) }
    val canSignIn = household.isNotBlank() && password.isNotEmpty() && !model.signingIn
    val submit = { if (canSignIn) model.signIn(household, password) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Logo()
        Spacer(Modifier.height(24.dp))
        Text(stringResource(Res.string.sign_in_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.sign_in_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (screen.signedOut) {
            Spacer(Modifier.height(16.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(stringResource(Res.string.sign_in_signed_out), modifier = Modifier.padding(16.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = household,
            onValueChange = { household = it },
            label = { Text(stringResource(Res.string.sign_in_household)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(Res.string.sign_in_password)) },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(
                            if (showPassword) Res.string.hide_password else Res.string.show_password,
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        model.signInProblem?.let { problem ->
            Spacer(Modifier.height(12.dp))
            Text(
                when (problem) {
                    is SignInProblem.Box -> problem.message
                    SignInProblem.WrongBox -> stringResource(Res.string.sign_in_wrong_box)
                    SignInProblem.Unreachable -> stringResource(Res.string.sign_in_unreachable)
                    SignInProblem.NotSetUp -> stringResource(Res.string.sign_in_not_set_up)
                },
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = submit, enabled = canSignIn, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (model.signingIn) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(Res.string.sign_in_button))
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = model::findAgain, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.sign_in_other_box))
        }
    }
}

/** A box without a household account: the welcome screen is in the browser. */
@Composable
fun NotSetUpScreen(model: AppModel) {
    PlatformBackHandler(onBack = model::findAgain)
    MessageScreen(
        title = stringResource(Res.string.not_set_up_title),
        body = stringResource(Res.string.not_set_up_body),
        action = stringResource(Res.string.not_set_up_open),
        onAction = model::openWelcome,
        secondAction = stringResource(Res.string.not_set_up_back),
        onSecondAction = model::findAgain,
    )
}
